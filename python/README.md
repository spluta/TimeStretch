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

### Examples

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

## Documentation

See the [Jupyter notebook](https://github.com/asness/TimeStretch/blob/master/python/NessStretch%20documentation.ipynb).
