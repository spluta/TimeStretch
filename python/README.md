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
See usage options with the `-h` option.
```
✗ ./nessstretch.py -h
usage: nessstretch.py [-h] [-n RATE_NUMERATOR] [-d RATE_DENOMINATOR]
                      [-v {2,4,8}]
                      [-w {boxcar,triang,blackman,hamming,hann,bartlett,flattop,parzen,bohman,blackmanharris,nuttall,barthann}]
                      infile outfile

positional arguments:
  infile                path to 16 bit wave source file
  outfile               path to write output file

optional arguments:
  -h, --help            show this help message and exit
  -n RATE_NUMERATOR, --rate_numerator RATE_NUMERATOR
                        numerator of the playback rate ratio, default is 1
  -d RATE_DENOMINATOR, --rate_denominator RATE_DENOMINATOR
                        denominator of the playback rate ratio, default is 8
  -v {2,4,8}, --overlap {2,4,8}
                        how many overlapping analysis and synthesis windows
                        will be used, default is 4
  -w {boxcar,triang,blackman,hamming,hann,bartlett,flattop,parzen,bohman,blackmanharris,nuttall,barthann}, --window {boxcar,triang,blackman,hamming,hann,bartlett,flattop,parzen,bohman,blackmanharris,nuttall,barthann}
                        what window shape will be used for analysis and
                        synthesis. Available options are: boxcar, triang,
                        blackman, hamming, hann, bartlett, flattop, parzen,
                        bohman, blackmanharris, nuttall, barthann
```

Input file must be a 16-bit signed integer wave file.

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

See the [Jupyter notebook](https://github.com/asness/TimeStretch/blob/master/python/NessStretch%20documentation.ipynb).
