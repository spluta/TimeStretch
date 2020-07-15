#!/usr/bin/env python
#
# Fancy STFT
#
# by Alex Ness (asness@gmail.com)
#
# Licence: CC BY-SA 3.0 US


import numpy as np
import scipy.signal
import scipy.io.wavfile

topWindow = 256
nBands = 9
nffts = [topWindow * pow(2, i) for i in range(nBands)]
binsPerBand = topWindow // 4
hops = [nfft // 4 for nfft in nffts]
noverlaps = [nfft - hop for nfft, hop in zip(nffts, hops)]

# higher bins contain more time data than lower bins,
# but we want all the time data in the same array:
# duplicate (repeat) samples in the lower bins
repeatFactors = [pow(2, n) for n in range(nBands)]

# where the frequencies live in the STFT
# special sauce for the low frequencies: use all bins below threshold
STFTBins = [(0, topWindow // 2 + 1)]
# use a fixed range of bins for the other frequencies
bottomBin = topWindow // 4 + 1
topBin = topWindow // 2 + 1 # includes adding one for range arguments
STFTBins += [(bottomBin, topBin) for i in range(nBands - 1)]
# list high frequency bins first (to make consistent with the lists above)
STFTBins = list(reversed(STFTBins))

# where the frequencies live in the FSTFT
# includes special sauce for the low frequencies
FSTFTBins = [(0, 2*binsPerBand + 1)]
FSTFTBins += [((n+2)*binsPerBand + 1, (n+3)*binsPerBand + 1) for n in range(nBands - 1)]
# list high frequencies first
FSTFTBins = list(reversed(FSTFTBins))

def p2i(r, th):
    th_norm = th % (2*np.pi)
    return r * np.exp(1j * th_norm)

def interp_stft(Zxx, n):
    '''Interpolate STFT data.
    Smaller n values mean more interpolation (more time samples).'''
    Zxx_freqs, Zxx_frames = Zxx.shape
    Zxx_mag = np.abs(Zxx)
    Zxx_phase = np.unwrap(np.angle(Zxx))

    magInterpFunc = scipy.interpolate.interp2d(range(Zxx_frames), range(Zxx_freqs), Zxx_mag, kind='cubic')
    Zxx_mag_interp = magInterpFunc(np.arange(0, Zxx_frames, n), range(Zxx_freqs))

    phaseInterpFunc = scipy.interpolate.interp2d(range(Zxx_frames), range(Zxx_freqs), Zxx_phase, kind='cubic')
    Zxx_phase_interp = phaseInterpFunc(np.arange(0, Zxx_frames, n), range(Zxx_freqs))

    Zxx_interp = p2i(Zxx_mag_interp, Zxx_phase_interp)
    #print(Zxx[10,:4], Zxx_interp[10,:16])
    return Zxx_interp

def fstft(x, fs):
    '''Fancy multiresulotion STFT.
    x is a 1D array of audio sample data.'''

    Zxx_sum = None
    numFrames = None
    freqs = None
    times = None

    print('Fancy STFT in progress.')
    for nfft, noverlap, rf, (lowSTFTBin, highSTFTBin) in zip(nffts, noverlaps, repeatFactors, STFTBins):
        f, t, Zxx = scipy.signal.stft(x, nfft=nfft, fs=fs, noverlap=noverlap, nperseg=nfft)
        print('nfft : %d, noverlap: %d, Zxx.shape: %s' % (nfft, noverlap, str(Zxx.shape)))
        # append some frequency data
        if freqs is None:
            freqs = f[lowSTFTBin:highSTFTBin]
        else:
            freqs = np.insert(freqs, 0, f[lowSTFTBin:highSTFTBin])

        if times is None:
            times = t

        # crop the bins
        Zxx_cropped = Zxx[lowSTFTBin:highSTFTBin,:]

        # add a range of bins from the current STFT to the start (bottom)
        # of the output array

        if Zxx_sum is None:
            Zxx_sum = Zxx_cropped
            numFreqs, numFrames = Zxx_sum.shape
        else:
            # interpolate, don't repeat!
            #Zxx_interp = np.repeat(Zxx_cropped, rf, axis=1)
            # (otherwise time-stretching sounds chunky)
            Zxx_interp = interp_stft(Zxx_cropped, 1.0/rf)
            Zxx_recropped = Zxx_interp[:,:numFrames]
            Zxx_sum = np.insert(Zxx_sum, 0, Zxx_recropped, axis=0)
    return freqs, times, Zxx_sum

def ifstft(Zxx_sum):
    '''Fancy multiresolution ISTFT.
    Zxx_sum is an FSTFT.'''

    print('Fancy ISTFT in progress.')
    x_sum = None

    for nfft, noverlap, rf, (lowSTFTBin, highSTFTBin), (lowFSTFTBin, highFSTFTBin) in zip(nffts, noverlaps, repeatFactors, STFTBins, FSTFTBins):

        # extract STFT bins and decimate frames
        Zxx = Zxx_sum[lowFSTFTBin:highFSTFTBin,::rf]
        numBins, numFrames = Zxx.shape

        # create an empty STFT of the appropriate size
        Zxx_pad = np.zeros((nfft//2 + 1, numFrames), dtype='complex128')
        print('nfft : %d, noverlap: %d, Zxx_pad.shape: %s' % (nfft, noverlap, str(Zxx_pad.shape)))

        # paste in the appropriate FSTFT bins
        Zxx_pad[lowSTFTBin:highSTFTBin] += Zxx

        # take the ISTFT with the given overlap
        t, x = scipy.signal.istft(Zxx_pad, nfft=nfft, noverlap=noverlap, nperseg=nfft)

        if x_sum is None:
            x_sum = x
        else:
            x_sum[:len(x)] += x
    return x_sum

def demo(audioInputPath, autioOutputPath):
    '''Use a mono audio file.'''

    # load an audio file as an array
    rate, data = scipy.io.wavfile.read(audioInputPath)

    # scale the audio data to [-1.0, 1.0]
    scale = pow(2, 15) # assume 16 bit audio
    data_float = data / float(scale)

    freqs, times, Zxx = fstft(data_float, rate)
    #print(freqs)

    x = ifstft(Zxx)
    print('writing audio. . .')
    scipy.io.wavfile.write(autioOutputPath, rate, np.int16(x*scale))


#demo('sources/charli-xcx_blame_L.wav', 'output/charli-xcx_blame_fstft.wav')
