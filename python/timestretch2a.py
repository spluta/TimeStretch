#!/usr/bin/env python
#
# Time stretch with fancy STFT
#
# by Alex Ness (asness@gmail.com)
#
# Licence: CC BY-SA 3.0 US

import numpy as np
import scipy
from os.path import join
from fstft2a import fstft, ifstft # fancy STFT
playbackRate = 0.01 # for time-stretching

# Mono input, stereo output
fnRoot = 'charli-xcx_blame'
fnChannels = ['%s_%s.wav' % (fnRoot, i) for i in 'LR']
inputFilePaths = [join('sources', f) for f in fnChannels]

def random_phases(Zxx):
    '''Zxx is a time-stretched STFT'''
    return np.exp(2 * np.pi * 1.j * np.random.rand(*Zxx.shape))

def render():

    outputFilePath = join('output', '%s_%s.wav' % (fnRoot, playbackRate))
    print('output file path is %s' % outputFilePath)

    x_slows = [None, None]

    for i, thisInput in enumerate(inputFilePaths):
        print('channel %s' % (i+1))

        print('loading audio file %s' % thisInput)
        sampleRate, x_int = scipy.io.wavfile.read(thisInput)
        x = x_int / 32768.0
        print('sample rate is %s' % sampleRate)

        f, t, Zxx = fstft(x, sampleRate)
        print('separating magnitude and phase. . .')
        Zxx_mag = np.abs(Zxx)
        freqs, frames = Zxx.shape

        print('interpolating. . .')
        interpFunc = scipy.interpolate.interp2d(range(frames), range(freqs), Zxx_mag, kind='linear')
        Zxx_stretched = interpFunc(np.arange(0, frames, playbackRate), range(freqs))

        # generate separate random phases for each channel
        print('generating random phases. . . ')
        Zxx_phases = random_phases(Zxx_stretched)

        print('randomizing STFT phases. . .')
        Zxx_random = Zxx_phases * Zxx_stretched

        x_slows[i] = ifstft(Zxx_random)

    print('creating audio array. . .')
    
    # Transpose array: see https://github.com/bastibe/SoundFile/issues/203
    audioArray = np.int16(np.array(x_slows).T*32768.0)

    print('writing audio. . .')
    scipy.io.wavfile.write(outputFilePath, sampleRate, audioArray)


render()
