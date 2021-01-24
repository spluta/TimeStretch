#!/usr/bin/env python
#
# Fancy STFT
#
# by Alex Ness (asness@gmail.com)
# and Jem Altieri (jem@jem.space)
#
# Licence: CC BY-SA 3.0 US

from os.path import join
import logging as log
import rwindow
import numpy as np
import scipy.signal
import sys

def p2r(radii, angles):
    '''Complex polar to rectangular'''
    return radii * np.exp(1j*angles)

def r2p(x):
    '''Complex rectangular to polar'''
    return np.abs(x), np.angle(x)

class AnalysisBand(object):
    def __init__(self, nfft, low_bin, high_bin, synthesis_window, phase_mode, nonnegative_phases, overlap):
        # windowing
        self.analysis_window_array = scipy.signal.get_window('hann', nfft)
        self.synthesis_window = synthesis_window
        if self.synthesis_window in rwindow.variable_window_funcs.keys():
            self.variable_window = True
            window_func = rwindow.variable_window_funcs[synthesis_window]
            self.xfade_table = window_func(self.hop_size)
            self.xfade_buffer = np.zeros(self.hop_size)
            self.overlap = 2
        else:
            self.variable_window = False
            self.synthesis_window_array = scipy.signal.get_window(self.synthesis_window, nfft)
            self.overlap = overlap
        # analysis variables
        self.nfft = nfft
        self.nrfft = nfft // 2 + 1
        self.hop_size = nfft // self.overlap
        # bandpass bins
        self.low_bin = low_bin
        self.high_bin = high_bin
        self.bins = self.high_bin + 1 - self.low_bin
        # phase
        self.phase_mode = phase_mode
        if self.phase_mode == 'interpolate':
            self.previous_input_phases = None
            self.output_phases = None
        self.nonnegative_phases = nonnegative_phases
        # phase correlation statistics
        self.r_sum = 0
        self.r_sum_abs = 0
        self.r_inverted = 0
    
    def pad_bandpass_frame(self, processed_bins):
        return np.concatenate([
            np.zeros(self.low_bin),
            processed_bins,
            np.zeros(self.nrfft - (self.high_bin + 1))
        ])

    def random_phases(self):
        return np.random.uniform(0, 2*np.pi, self.bins)

    def process_frame(self, padded_input_signal, current_input_time, current_output_time, mix_bus, playback_rate):
        analysis_frame = padded_input_signal[int(current_input_time) : int(current_input_time) + self.nfft] * self.analysis_window_array
        rfft = np.fft.rfft(analysis_frame)
        if self.phase_mode == 'randomize':
            amplitudes = np.abs(rfft)
            amplitudes_bandpass = amplitudes[self.low_bin : self.high_bin + 1]
            complex_phases = np.exp(self.random_phases() * 1j)
            bandpass_frame = self.pad_bandpass_frame(amplitudes_bandpass * complex_phases)
        elif self.phase_mode == 'interpolate':
            amplitudes, input_phases = r2p(rfft)
            amplitudes_bandpass = amplitudes[self.low_bin : self.high_bin + 1]
            input_phases_bandpass = input_phases[self.low_bin : self.high_bin + 1]
            if self.output_phases is None:
                self.output_phases = self.random_phases()
            else:
                input_phase_diffs = (input_phases_bandpass - self.previous_input_phases) % (2*np.pi)
                output_phase_diffs = (input_phase_diffs / playback_rate) % (2*np.pi)
                self.output_phases = (self.output_phases + output_phase_diffs) % (2*np.pi)
            bandpass_frame = self.pad_bandpass_frame(p2r(amplitudes_bandpass, self.output_phases))
            self.previous_input_phases = input_phases_bandpass
        else: # self.phase_mode == 'keep'
            bandpass_frame = self.pad_bandpass_frame(rfft[self.low_bin : self.high_bin + 1])

        ifft = scipy.fft.irfft(bandpass_frame)
        
        if self.variable_window:
            ifft_l, ifft_r = ifft[:self.hop_size], ifft[self.hop_size:]
            if sum(self.xfade_buffer) == 0 or sum(ifft_l) == 0:
                r = 1
            else:
                r = np.corrcoef(ifft_l, self.xfade_buffer)[0,1]
            self.r_sum += r
            if self.nonnegative_phases and r < 0:
                self.r_inverted += 1
                r *= -1
                ifft_l *= -1
                ifft_r *= -1
                self.r_sum_abs += r
            mix = rwindow.xfade_with_windows(ifft_l, self.xfade_buffer, r, self.xfade_table)
            mix_bus[int(current_output_time) : int(current_output_time) + self.hop_size] += mix
            self.xfade_buffer = ifft_r
        else:
            mix = ifft * self.synthesis_window_array
            mix_bus[int(current_output_time) : int(current_output_time) + self.nfft] += mix

    def stretch(self, input_signal, playback_rate, channel, mix_bus, sample_rate, fixed_length):
        if self.phase_mode in ('randomize', 'interpolate'):
            padded_input_signal = np.concatenate([np.zeros(self.nfft//2), input_signal, np.zeros(self.nfft//2)])
        else: # self.phase_mode == 'keep'
            padded_input_signal = np.concatenate([input_signal, np.zeros(self.nfft)])
        total_length = len(padded_input_signal) - self.nfft
        if fixed_length > 0:
            input_end_time = min(sample_rate * playback_rate * fixed_length, total_length)
        else:
            input_end_time = total_length
        current_input_time = 0
        current_output_time = 0
        num_frames = 0
        while current_input_time < input_end_time:
            progress = int(100 * current_input_time / input_end_time)
            sys.stdout.write(f'Channel {channel + 1}, FFT size {self.nfft:6d}: {progress:3d}% complete \r')
            sys.stdout.flush()
            self.process_frame(padded_input_signal, current_input_time, current_output_time, mix_bus, playback_rate)
            current_input_time += self.hop_size * playback_rate
            current_output_time += self.hop_size
            num_frames += 1
        print(f'Channel {channel + 1}, FFT size {self.nfft:6d}: 100% complete')
        log.debug(f'Number of frames: {num_frames}')
        if self.variable_window:
            log.debug(f'Average frame correlation without phase correction r_av = {self.r_sum / num_frames}')
            if self.nonnegative_phases:
                log.debug(f'Number of negative frame correlations: {self.r_inverted}')
                log.debug(f'Average frame correlation with phase correction r_av_abs = {self.r_sum_abs / num_frames}')

def fancy_stretch(temp_dir, input_signal, playback_rate, channel, preset, input_sample_rate, fixed_length):
    max_nfft = max(preset.nfft)
    target_length = np.ceil(len(input_signal) / playback_rate) + max_nfft
    mix_bus_path = join(temp_dir, f'channel-{channel}.dat')
    mix_bus = np.memmap(mix_bus_path, dtype='float32', mode='w+', shape=int(target_length))
    for index, band_preset in preset.iterrows():
        log.info(f'Stretching size {band_preset.nfft}')
        log.debug(f'Settings: low_bin={band_preset.low_bin}, '
                  f'high_bin={band_preset.high_bin}, '
                  f'synthesis_window={band_preset.synthesis_window}, '
                  f'phase_mode={band_preset.phase_mode}, '
                  f'nonnegative_phases={band_preset.nonnegative_phases}, '
                  f'overlap={band_preset.overlap}, '
                  f'fixed_length={fixed_length}')
        band = AnalysisBand(
            band_preset.nfft,
            band_preset.low_bin,
            band_preset.high_bin,
            band_preset.synthesis_window,
            band_preset.phase_mode,
            band_preset.nonnegative_phases,
            band_preset.overlap)
        band.stretch(
            input_signal,
            playback_rate,
            channel,
            mix_bus,
            input_sample_rate,
            fixed_length)
    return mix_bus
