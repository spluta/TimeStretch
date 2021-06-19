# ness_stretch

Algorithm by [Alex Ness](alexness.bandcamp.com) and [Sam Pluta](sampluta.com)

Rust version by [Sam Pluta](sampluta.com)

Install the command-line version with homebrew:

```
brew tap spluta/ness_stretch
brew install ness_stretch
```
then
```
ness_stretch -h
```
for the help.

SuperCollider and python versions of the NessStretch can be found at [https://github.com/spluta/TimeStretch](https://github.com/spluta/TimeStretch).

Implements a phase randomized real fft (using rustfft) time stretch algorithm, the NessStretch, which splits the original sound file into 9 discrete frequency bands, and uses a decreasing frame size to correspond to increasing frequency. Starting with a largest frame of 65536, the algorithm will use the following frequency band/frame size breakdown (assuming 44100 Hz input):

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
