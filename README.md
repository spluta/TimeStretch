# TimeStretch

Python3 version by [Alex Ness](alexness.bandcamp.com) and [Jem Altieri](https://www.jem.space/)

SuperCollider and Rust versions by [Sam Pluta](sampluta.com)

Implements a phase randomized Real FFT time stretch algorithm, the NessStretch, which splits the original sound file into 9 discrete frequency bands, and uses a decreasing frame size to correspond to increasing frequency. Starting with a largest frame of 65536, the algorithm will use the following frequency band/frame size breakdown (assuming 44100 Hz input):

0-86 Hz : 65536 frames,
86-172 : 32768,
172-344 : 16384,
344-689 : 8192,
689-1378 : 4096,
1378-2756 : 2048,
2756-5512 : 1024,
5512-11025 : 512,
11025-22050 : 256.

The NessStretch is a refinement of [Paul Nasca](http://www.paulnasca.com/)'s excellent [PaulStretch](http://hypermammut.sourceforge.net/paulstretch/) algorithm.  PaulStretch uses a single frame size throughout the entire frequency range.  The NessStretch's layered analysis bands are a better match for human frequency perception, and do a better job of resolving shorter, noisier high-frequency sounds (sibilance, snares, etc.).

See the [ICMC paper](https://github.com/spluta/TimeStretch/blob/main/NessStretchICMC_Final.pdf) for more details. Or just run it and give it a listen.

## Installation

## Rust

For an optimized version of the NessStretch, use the command-line Rust version, which can be installed in a couple of different ways:

1) via homebrew (mac only), by running:

```
brew tap spluta/ness_stretch
brew install ness_stretch
```
then
```
ness_stretch -h
```
for the help.

2) Rust cargo users can install with cargo:

```
cargo install ness_stretch
```

Linux and Windows builds (untested auto builds using GitHub actions) are found here:

https://github.com/spluta/ness_stretch/actions/runs/1087257841

Or download the Rust source and compile using cargo. The rust version will be very fast on Intel Macs and very slow on Arm machines (until rustfft gets updated).

## Python

Requires numpy, scipy, and pandas. Using `virtualenv`:

```
virtualenv .env
. .env/bin/activate
pip install -r requirements.txt
```

See the Readme inside the python directory for further instructions.

## SuperCollider

The SuperCollider version can be installed by running:

```
Quarks.install("TimeStretch")
```
or downloading the source code and either 1) adding the source code directory to SC's path or placing the source code folder inside the Extensions folder (Platform.systemExtensionDir)
