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
from tempfile import TemporaryDirectory

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
DEFAULT_PHASE_RANDOMIZATION = True

def norm_factor(signal, reference_signal):
    max_ref = np.max(reference_signal)
    max_sig = np.max(signal)
    factor = max_ref / max_sig
    return factor

def render(infile, outfile, playback_rate, overlap, window, randomize_phases):
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
    with TemporaryDirectory(dir='.') as temp_dir: 
        for channel in range(n_channels):
            print(f'processing channel {channel+1}')
            input_channel = normalized_input_data[:, channel]
            output.append(fancy_stretch(temp_dir, input_channel, playback_rate, channel, overlap=overlap, window=window, randomize_phases=randomize_phases))
        print('normalizing audio')
        factor = norm_factor(output, normalized_input_data)
        audio_array_path = join(temp_dir, 'audio.dat')
        audio_array_shape = (n_channels, len(output))
        audio_array = np.memmap(audio_array_path, dtype='int16', mode='w+', shape=audio_array_shape)
        # Transpose array: see https://github.com/bastibe/SoundFile/issues/203
        audio_array = np.int16(np.array(output).T * factor * max_dtype_val)
        print('writing audio')
        scipy.io.wavfile.write(outfile, input_sample_rate, audio_array)
        print(f'output file path is {args.outfile.name}')
    print(f'deleting temporary files')
        
def perform_stretch(args):
    playback_rate = args.rate_numerator / args.rate_denominator
    render(args.infile, args.outfile, playback_rate, args.overlap, args.window, args.randomize_phases)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        'infile',
        type=argparse.FileType('rb'),
        help="path to 16 bit wave source file")
    parser.add_argument(
        'outfile',
        type=argparse.FileType('wb'),
        help="path to write output file")
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
    parser.add_argument(
        '-r', '--randomize-phases',
        dest='randomize_phases', 
        action='store_true',
        help=f'Randomize the analysis bin phases before resynthesis, default is {not DEFAULT_PHASE_RANDOMIZATION}')
    parser.add_argument(
        '-k', '--keep-phases',
        dest='randomize_phases', 
        action='store_false',
        help=f'Preserve analysis bin phases, default is {DEFAULT_PHASE_RANDOMIZATION}')
    parser.set_defaults(randomize_phases=True)
    args = parser.parse_args()
    perform_stretch(args)
