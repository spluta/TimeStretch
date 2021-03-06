# TimeStretch

Python3 version by [Alex Ness](alexness.bandcamp.com) and [Jem Altieri](https://www.jem.space/)

SuperCollider version by [Sam Pluta](sampluta.com)

Implements a phase randomized FFT (SC) or STFT (Python) time stretch algorithm, the NessStretch, which splits the original sound file into 9 discrete frequency bands, and uses a decreasing frame size to correspond to increasing frequency. Starting with a largest frame of 65536, the algorithm will use the following frequency band/frame size breakdown (assuming 44100 Hz input):

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

Dependency: SignalBox Quark by Jo Anderson and, optionally, the FluCoMa Library (flucoma.org).

Special thanks to Jean-Philippe Drecourt (http://drecourt.com/) for his SuperCollider implementation of Paulstretch, which was a huge influence on this code.
