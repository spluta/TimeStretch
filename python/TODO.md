* Short term:
  * [ ] Add temp file cleanup (and add an option to leave the temp file).
  * [ ] Add a memory map for audio_array?
  * [ ] Documentation
    * [ ] Add click examples comparing PaulStretch to NessStretch.
    * [ ] Add time compression examples.
  * [ ] Send fan mail to Paul Nasca

* Longer term:
  * [ ] Make the audio formats more flexible.
  * [ ] Make the analysis settings more flexible.
    * [ ] Accept a CSV of fancy_bands (frequency, low bin, high bin) data.
    * [ ] Add an option to turn off the phase scrambling.

* Questions:
  * Why do the shorter-frame time stretches take so much longer (proportionally, almost) than the longer-frame ones?  It it just because there are so many frames to render?  Is there something simple we can do to speed them up?
