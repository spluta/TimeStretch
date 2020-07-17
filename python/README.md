# nessstretch.py

Python implementation of the NessStretch algorithm.

## Requirements
Requires numpy and scipy. Using `virtualenv`:
```
virtualenv .env
. .env/bin/activate
pip install -r requirements.txt
```

## Usage
Input file must be a 16-bit signed integer wave file.

```
./nessstretch.py INPUT_FILE OUTPUT_FILE -n PLAYBACK_RATE_NUMERATOR -d PLAYBACK_RATE_DENOMINATOR
```

### Examples
Stretch an input file to 8 times the original length (default):
```
./nessstretch.py input.wav output.wav
```

Stretch an input file to 100 times the original length:
```
./nessstretch.py input.wav output.wav -d 100
```

Stretch an input file to 125% the original length (80% playback speed):
```
./nessstretch.py input.wav output.wav -d 5 -n 4
```

## Documentation

The NessStretch **algorithm** is similar to the [PaulStretch algorithm](http://www.paulnasca.com/algorithms-created-by-me#TOC-PaulStretch-extreme-sound-stretching-algorithm).  The only substantive difference is that NessStretch uses different FFT window sizes for different frequency ranges.

The NessStretch **implementation** is *somewhat* similar to the PaulStretch stereo Python implementation:

* Both implementations generate timestretch frames by stepping through the input sample array more slowly (by the timestretch factor) than the output sample array.  This creates de facto spectral interpolation.
* Both use convolution with unit-magnitude white noise to randomize bin phases.
* Both use an [RFFT](https://numpy.org/doc/stable/reference/generated/numpy.fft.rfft.html) to optimize analysis and synthesis for real-valued input and output.

There are, however, a couple implementation differences that are not purely cosmetic:

* PaulStretch writes synthesis frames to a buffer, and the buffer content is appended to an output audio file.  This is efficient (there's no need for large intermediate files), but the buffer math is a bit of a headache, and there's no simple way to mix different output layers together.  Instead, NessStretch loads a large mix_bus array for each channel, to which it adds the output from each time-stretched frequency band.  Unless PaulStretch, this generates some large intermediate files (roughly 100 MB per channel per minute), but the process is more transparent, and mixing the frequency bands together is trivial.
*  PaulStretch doesn't normalize the output audio (which makes sense, because there's no simple way to normalize an audio file "in real time"; you would have to use some sort of dynamics processing).  NessStretch normalizes the maximum output to the maximum input (all  the audio data is stored in arrays ahead of time, so this is easy to do).

Some miscellaneous script details that may not be obvious:

* RFFT bins: an RFFT returns nfft // 2 + 1 bins total.  Bin 0 is the DC component (0 Hz), and bin nfft // 2 is the Nyquist component (sampling rate / 2 Hz).
* Input file padding: pad the input audio with nfft // 2 samples on either end to center the analysis windows correctly.  (It's easy to check this by time-stretching an impulse signal: the output should sound like a symmetrical filter sweep.)
* fancy_bands: the bin range tuple is designed like a Python range argument.  (The high bin **index** is actually 128.)
* target_length: I'm not sure if this calculation is exactly right, but it works.
