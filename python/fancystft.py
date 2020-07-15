#!/usr/bin/env python
#
# Fancy STFT
#
# by Alex Ness (asness@gmail.com)
# and Jem Altieri (jem@jem.space)
#
# Licence: CC BY-SA 3.0 US

from functools import reduce
import numpy as np
import scipy.interpolate
import scipy.io.wavfile
import scipy.signal

fancy_bands = {
    256: (65, 128),
    512: (65, 129),
    1024: (65, 129),
    2048: (65, 129),
    4096: (65, 129),
    8192: (65, 129),
    16384: (65, 129),
    32768: (65, 129),
    65536: (0, 129),
    }

class AnalysisBand(object):
    def __init__(self, nfft=None, hop_size=None, times=None, zxx=None, window=None):
        self.nfft = nfft
        self.hop_size = hop_size
        self.times = times
        self.window = window
        self.frame_size = zxx.shape[0]
        self.amplitudes = np.abs(zxx)

def analyze_band_stft(input_signal, nfft, overlap, window='hann'):
    # print(f"nfft = {nfft}")
    hop_size = nfft // overlap
    noverlap = nfft - hop_size
    freqs, times, zxx = scipy.signal.stft(input_signal, nperseg=nfft, noverlap=noverlap, window=window)
    # print(f"analyze band zxx shape {zxx.shape}")
    return AnalysisBand(nfft=nfft, hop_size=hop_size, times=times, zxx=zxx, window=window)

def analyze_band_rfft(input_signal, nfft, overlap, window='hann'):
    hop_size = nfft // overlap
    window_array = scipy.signal.get_window(window, nfft)
    current_time = 0
    zxx = []
    times = []
    while current_time < len(input_signal)-100000:
        analysis_frame = input_signal[current_time : current_time + nfft] * window_array
        zxx.append(np.fft.rfft(analysis_frame))
        times.append(current_time)
        current_time += hop_size
    # print(f"zxx len {len(zxx)}")
    return AnalysisBand(nfft=nfft, hop_size=hop_size, times=np.array(times), zxx=np.swapaxes(np.array(zxx),0,1), window=window)


def analyze(input_signal, overlap, nffts, window='hann'):
    analysis = {}
    for nfft in nffts:
        print(f'running analysis for size {nfft}')
        analysis[nfft] = analyze_band_rfft(input_signal, nfft, overlap, window=window)
    return analysis

def bandpass_filter_impulse(frame_size, low_bin, high_bin):
    # print(frame_size, low_bin, high_bin)
    return np.concatenate([np.zeros(low_bin), np.ones(1 + high_bin - low_bin), np.zeros(frame_size - high_bin - 1)])

def synthesize_frame(frame_amplitudes, filter_impulse, window_array):
    # TODO: only make random phases for bins we're going to use
    phases = np.random.uniform(0, 2*np.pi, frame_amplitudes.shape) * 1j
    # print(frame_amplitudes.shape)
    # print(filter_impulse.shape)
    # print(window_array.shape)
    frame = frame_amplitudes * np.exp(phases) * filter_impulse
    # foo = scipy.fft.irfft(frame)
    # print(foo.shape)
    frame_output = scipy.fft.irfft(frame) * window_array
    return frame_output

def synthesize_band(band, low_bin, high_bin, playback_rate):
    # print(f"band shape: {band.amplitudes.shape}")
    window_array = scipy.signal.get_window(band.window, band.nfft)
    filter_impulse = bandpass_filter_impulse(band.frame_size, low_bin, high_bin)
    interp_func = scipy.interpolate.interp1d(band.times, band.amplitudes)
    last_time_in_source = band.times[-1]
    target_length = last_time_in_source / playback_rate
    hop_size = band.hop_size
    cur_output_time = 0
    # print(f"target_length: {target_length}")
    frames_processed = 0
    band_output = np.zeros(int(target_length) + band.nfft)
    while cur_output_time < target_length:
        source_time = cur_output_time * playback_rate
        frame_amplitudes = interp_func(source_time)
        frame_output = synthesize_frame(frame_amplitudes, filter_impulse, window_array)
        # if np.max(frame_output) < 0.01:
        #     print(f"max is {np.max(frame_output)}")
        # print(frame_output.shape)
        # print(f"frame output shape: {frame_output.shape}")
        # print(f"target region shape: {band_output[cur_output_time:(cur_output_time + frame_output.shape[0])].shape}")
        band_output[cur_output_time:(cur_output_time + len(frame_output))] += frame_output
        cur_output_time += hop_size
        # frames_processed += 1
        # print(f"cur_output_time: {cur_output_time}")
        # if (frames_processed % 100 == 0):
        #     pct_done = cur_output_time / target_length
        #     print(f"percent done: {pct_done}")
    return band_output

def fancy_stretch(input_signal, playback_rate, overlap=4):
    band_outputs = {}
    analysis = analyze(input_signal, overlap, fancy_bands.keys(), window='hann')
    for nfft, analysis_band in analysis.items():
        low_bin, high_bin = fancy_bands[nfft]
        print(f'running synthesis for size {nfft}')
        band_outputs[nfft] = synthesize_band(analysis_band, low_bin, high_bin, playback_rate)
    max_length = max([len(band_output) for band_output in band_outputs.values()])
    zeros = np.zeros(max_length)
    mix_bus = np.copy(zeros)
    for band_output in band_outputs.values():
        temp = np.copy(zeros)
        temp[0:len(band_output)] = band_output
        mix_bus += temp
    return mix_bus
