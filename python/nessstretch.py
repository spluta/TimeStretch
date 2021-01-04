#!/usr/bin/env python3
#
# Time stretch with fancy STFT
#
# by Alex Ness (asness@gmail.com)
# and Jem Altieri (jem@jem.space)
#
# Licence: CC BY-SA 3.0 US

import argparse
import logging as log
import numpy as np
import pandas as pd
import scipy.io.wavfile
import datetime as dt
from os.path import join
from fancystft import fancy_stretch
from tempfile import TemporaryDirectory

DEFAULT_RATE = '0.125'
DEFAULT_PRESET_CSV = 'presets.csv'
DEFAULT_PRESET = 'katy_perry'

def norm_factor(signal, reference_signal):
    max_ref = np.max(reference_signal)
    max_sig = np.max(signal)
    factor = max_ref / max_sig
    return factor

def render(infile, outfile, rate, preset_file, preset_name):
    log.debug(f'Loading preset "{preset_name}" from file "{preset_file}"')
    all_presets = pd.read_csv(preset_file)
    preset = all_presets[all_presets['preset'] == preset_name]
    log.debug(f'Loading input file "{infile.name}"')
    input_sample_rate, raw_input_data = scipy.io.wavfile.read(infile)
    n_input_frames, n_channels = raw_input_data.shape
    input_dtype = raw_input_data.dtype
    log.debug(f'Input channels: {n_channels}')
    log.debug(f'Input frames: {n_input_frames}')
    log.debug(f'Input sample type: {input_dtype}')
    log.debug(f'Input sample rate: {input_sample_rate}')
    if '/' in rate:
        num, denom = rate.split('/')
        rate_val = float(num) / float(denom)
        log.debug(f'Stretching at rate {rate} = {rate_val}')
    else:
        rate_val = float(rate)
        log.debug(f'Stretching at rate {rate_val}')
    max_dtype_val = np.iinfo(input_dtype).max
    normalized_input_data = raw_input_data / max_dtype_val
    output = []
    with TemporaryDirectory(dir='.') as temp_dir: 
        for channel in range(n_channels):
            log.info(f'Processing channel {channel+1}')
            input_channel = normalized_input_data[:, channel]
            channel_stretch = fancy_stretch(temp_dir, input_channel, rate_val, channel, preset)
            output.append(channel_stretch)
        log.info('Normalizing audio')
        factor = norm_factor(output, normalized_input_data)
        audio_array_path = join(temp_dir, 'audio.dat')
        audio_array_shape = (len(output[0]), n_channels)
        audio_array = np.memmap(audio_array_path, dtype='int16', mode='w+', shape=audio_array_shape)
        # Transpose array: see https://github.com/bastibe/SoundFile/issues/203
        audio_array[:,:] = np.int16(np.array(output).T * factor * max_dtype_val)
        log.info(f'Writing audio to "{args.outfile.name}"')
        scipy.io.wavfile.write(outfile, input_sample_rate, audio_array)
    log.info(f'Deleting temporary files')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument(
        'infile',
        type=argparse.FileType('rb'),
        help='path to 16-bit wave source file')
    parser.add_argument(
        'outfile',
        type=argparse.FileType('wb'),
        help='path to write output file')
    parser.add_argument(
        '-r', '--rate',
        type=str,
        default=DEFAULT_RATE,
        help=f'playback rate (as either a decimal or a fraction written as p/q), default is {DEFAULT_RATE}')
    parser.add_argument(
        '-p', '--preset',
        default=DEFAULT_PRESET,
        help=f'preset to use, default is {DEFAULT_PRESET}')
    parser.add_argument(
        '-f', '--preset-file',
        default=DEFAULT_PRESET_CSV,
        help=f'path to preset file CSV, default is {DEFAULT_PRESET_CSV}')
    parser.add_argument(
        '-v', '--verbose',
        action='store_true',
        help=f'show debugging messages, default is False')
    parser.add_argument(
        '-l', '--log',
        action='store_true',
        help=f'write logging messages to a file, default is False')
    args = parser.parse_args()
    if args.verbose:
        log_level=log.DEBUG
    else:
        log_level=log.INFO
    if args.log:
        now_str = dt.datetime.now().strftime('%Y%m%d%H%M%S')
        log_filename = f'nessstretch_{now_str}.log'
        log.basicConfig(filename=log_filename, level=log_level, format='%(asctime)s %(message)s')
    else:
        log.basicConfig(level=log_level, format='%(asctime)s %(message)s')
    render(args.infile, args.outfile, args.rate, args.preset_file, args.preset)
