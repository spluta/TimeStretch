# nessstretch.py

Python implementation of the NessStretch algorithm.

## Requirements

Requires numpy, scipy, and pandas. Using `virtualenv`:

```
virtualenv .env
. .env/bin/activate
pip install -r requirements.txt
```

## Usage

See usage options with the `-h` option:

```
./nessstretch.py -h

usage: nessstretch.py [-h] [-r RATE] [-p PRESET] [-f PRESET_FILE] [-v] [-l]
                      infile outfile

positional arguments:
  infile                path to 16-bit wave source file
  outfile               path to write output file

optional arguments:
  -h, --help            show this help message and exit
  -r RATE, --rate RATE  playback rate (as either a decimal or a fraction
                        written as p/q), default is 0.125
  -p PRESET, --preset PRESET
                        preset to use, default is katy_perry
  -f PRESET_FILE, --preset-file PRESET_FILE
                        path to preset file CSV, default is presets.csv
  -v, --verbose         show debugging messages, default is False
  -l, --log             write logging messages to a file, default is False
```

Input file must be a 16-bit signed integer wave file.

## Examples

Stretch an input file to 8 times the original length (default):
```
./nessstretch.py input.wav output.wav
```

Stretch an input file to 100 times the original length (1% playback speed):
```
./nessstretch.py input.wav output.wav -r 0.01
```

Stretch an input file to 5/4 the original length (4/5 playback speed):
```
./nessstretch.py input.wav output.wav -r 4/5
```

## Presets

STFT presets are stored in a CSV file (`presets.csv` by default).  Each preset consists of multiple rows, each of which specifies an STFT bandpass setting for a different frequency range.  The script processes these bandpassed STFTs in series, then sums and normalizes the output.  The fields are as follows:

* `preset`: name of the preset setting
* `nfft`: size of the frequency band STFT window (in samples)
* `low_bin`: index of the lowest passed STFT bin
* `high_bin`: index of the highest passed STFT bin
* `synthesis_window`: STFT synthesis window name, for which there are two types of options:
  1. Any window function recognized by [scipy.signal.windows](https://docs.scipy.org/doc/scipy/reference/signal.windows.html#module-scipy.signal.windows).  
  2. A correlation-varying window `w_tan`, `w_scl`, or `w_scl2`.  (See the [ICMC paper](https://github.com/asness/TimeStretch/blob/master/python/NessStretchICMC_Final.pdf) for technical details on the windowing.)
* `phase_mode`: how to process STFT phases:
  1. `randomize` is the preferred setting for stretches.
  2. `keep` may be appropriate for time compression, but otherwise sounds wrong.
  3. `interpolate` is an experimental setting that creates interesting beating effects with harmonic sounds.
* `nonnegative_phases`: whether or not to invert negative phases (0 means don't invert them; 1 means invert them)
* `overlap`: density of window overlaps (should be a power of 2, and at least 2).

The preset name evokes the type of input that will sound good with that particular setting, or refers to a [Bandcamp album](https://bandcamp.com/tag/nessstretch) using that setting:

* `katy_perry`: the default setting: good for pop music
* [`josquin`](https://alexness.bandcamp.com/album/the-secret-place): longer windows: good for choral music
* [`waterloo_station`](https://alexness.bandcamp.com/album/beyond-all-coming-and-going): shorter windows, no phase correction: good for dense soundscapes
*  [`domglocken`](https://alexness.bandcamp.com/album/domglocken): interpolated phases: interesting experimental effect with harmonic sounds
* `paulstretch`: classic single-STFT PaulStretch setting with large windows

For even better results, we recommend splitting harmonic, transient, and percussive components and processing each separately.  A setting labeled `*_transients` or `*_percussive` has been fine-tuned for transient or percussive input.

## Algorithm details

For more information about the algorithm, see the [ICMC 2021 paper](https://github.com/asness/TimeStretch/blob/master/python/NessStretchICMC_Final.pdf).
