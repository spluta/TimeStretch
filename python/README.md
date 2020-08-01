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

See the [Jupyter notebook](https://github.com/asness/TimeStretch/blob/master/python/NessStretch%20documentation.ipynb).
