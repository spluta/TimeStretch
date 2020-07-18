#!/usr/bin/env python
#
# Fancy STFT
#
# by Alex Ness (asness@gmail.com)
# and Jem Altieri (jem@jem.space)
#
# Licence: CC BY-SA 3.0 US

from functools import reduce
from tempfile import mkdtemp
from os.path import join
import numpy as np
import scipy.interpolate
import scipy.io.wavfile
import scipy.signal
import sys

fancy_bands = {
    256: (65, 129),
    512: (65, 129),
    1024: (65, 129),
    2048: (65, 129),
    4096: (65, 129),
    8192: (65, 129),
    16384: (65, 129),
    32768: (65, 129),
    65536: (0, 129),
    }

temp_dir = mkdtemp(dir='.')
    
class AnalysisBand(object):
    def __init__(self, nfft=None, overlap=None, window=None):
        self.nfft = nfft
        self.nrfft = nfft // 2 + 1
        self.overlap = overlap
        self.hop_size = nfft // overlap
        self.window = window
        self.window_array = scipy.signal.get_window(window, nfft)
        self.low_bin, self.high_bin = fancy_bands[nfft]
        self.bins = self.high_bin - self.low_bin

    def bandpass_filter_impulse(self):
        phases = np.random.uniform(0, 2 * np.pi, self.bins) * 1j
        return np.concatenate([np.zeros(self.low_bin), np.exp(phases), np.zeros(self.nrfft - self.high_bin)])
    
    def process_frame(self, padded_input_signal, current_input_time, current_output_time, mix_bus):
        analysis_frame = padded_input_signal[int(current_input_time) : int(current_input_time) + self.nfft] * self.window_array
        rfft = np.fft.rfft(analysis_frame)
        amplitudes = np.abs(rfft)
        bandpass_frame = amplitudes * self.bandpass_filter_impulse()
        frame_output = scipy.fft.irfft(bandpass_frame) * self.window_array
        mix_bus[int(current_output_time) : int(current_output_time) + self.nfft] += frame_output
    
    def stretch(self, input_signal, playback_rate, mix_bus):
        padded_input_signal = np.concatenate([np.zeros(self.nfft//2), input_signal, np.zeros(self.nfft//2)])
        input_end_time = len(padded_input_signal) - self.nfft
        current_input_time = 0
        current_output_time = 0
        while current_input_time < input_end_time:
            progress = int(100 * current_input_time / input_end_time)
            sys.stdout.write(f'\t{progress}% complete \r')
            sys.stdout.flush()
            self.process_frame(padded_input_signal, current_input_time, current_output_time, mix_bus)
            current_input_time += self.hop_size * playback_rate
            current_output_time += self.hop_size
        print('\t100% complete')

def fancy_stretch(input_signal, playback_rate, channel, overlap=4, window='hann'):
    target_length = np.ceil(len(input_signal) / playback_rate) + max(fancy_bands.keys())
    mix_bus_path = join(temp_dir, f'channel-{channel}.dat')
    mix_bus = np.memmap(mix_bus_path, dtype='float32', mode='w+', shape=int(target_length))
    for nfft in fancy_bands.keys():
        band = AnalysisBand(nfft, overlap, window)
        print(f'stretching size {nfft}')
        band.stretch(input_signal, playback_rate, mix_bus)
    return mix_bus
