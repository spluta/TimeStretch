#!/usr/bin/env python
#
# Time stretch with fancy STFT
#
# by Alex Ness (asness@gmail.com)
# and Jem Altieri (jem@jem.space)
#
# Licence: CC BY-SA 3.0 US

import argparse
from os.path import join
import numpy as np
import scipy
from fancystft import fancy_stretch

DEFAULT_RATE_NUMERATOR = 1
DEFAULT_RATE_DENOMINATOR = 8
DEFAULT_OVERLAP = 4
WINDOW_TYPES = [
    'boxcar',
    'triang',
    'blackman',
    'hamming',
    'hann',
    'bartlett',
    'flattop',
    'parzen',
    'bohman',
    'blackmanharris',
    'nuttall',
    'barthann',
]
DEFAULT_WINDOW = 'hann'

def norm_factor(signal, reference_signal):
    max_ref = np.max(reference_signal)
    max_sig = np.max(signal)
    factor = max_ref / max_sig
    return factor

def render(infile, outfile, playback_rate, overlap, window):
    print(f'loading input file {infile.name}')
    input_sample_rate, raw_input_data = scipy.io.wavfile.read(infile)
    n_input_frames, n_channels = raw_input_data.shape
    input_dtype = raw_input_data.dtype
    print(f'Input channels: {n_channels}')
    print(f'Input frames: {n_input_frames}')
    print(f'Input sample type: {input_dtype}')
    print(f'Input sample rate: {input_sample_rate}')
    # TODO: handle float input
    max_dtype_val = np.iinfo(input_dtype).max
    normalized_input_data = raw_input_data / max_dtype_val
    output = []
    for channel in range(n_channels):
        print(f'processing channel {channel+1}')
        input_channel = normalized_input_data[:, channel]
        output.append(fancy_stretch(input_channel, playback_rate, channel, overlap=overlap, window=window))
    print('normalizing audio')
    factor = norm_factor(output, normalized_input_data)
    # Transpose array: see https://github.com/bastibe/SoundFile/issues/203
    audio_array = np.int16(np.array(output).T * factor * max_dtype_val)
    print('writing audio')
    scipy.io.wavfile.write(outfile, input_sample_rate, audio_array)
    print(f'output file path is {args.outfile.name}')

def perform_stretch(args):
    playback_rate = args.rate_numerator / args.rate_denominator
    render(args.infile, args.outfile, playback_rate, args.overlap, args.window)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument('infile', type=argparse.FileType('rb'))
    parser.add_argument('outfile', type=argparse.FileType('wb'))
    parser.add_argument(
        '-n', '--rate_numerator',
        type=float,
        default=DEFAULT_RATE_NUMERATOR,
        help=f'numerator of the playback rate ratio, default is {DEFAULT_RATE_NUMERATOR}')
    parser.add_argument(
        '-d', '--rate_denominator',
        type=float,
        default=DEFAULT_RATE_DENOMINATOR,
        help=f'denominator of the playback rate ratio, default is {DEFAULT_RATE_DENOMINATOR}')
    parser.add_argument(
        '-v', '--overlap',
        choices=[2,4,8],
        default=DEFAULT_OVERLAP,
        help=f'how many overlapping analysis and synthesis windows will be used, default is {DEFAULT_OVERLAP}')
    parser.add_argument(
        '-w', '--window',
        choices=WINDOW_TYPES,
        default=DEFAULT_WINDOW,
        help=f"what window shape will be used for analysis and synthesis. Available options are: {', '.join(WINDOW_TYPES)}")
    args = parser.parse_args()
    perform_stretch(args)
