* Short term:
  * [x] Add temp file cleanup. (completed 20200801)
  * [x] Add a memory map for audio_array.  (completed 20200801)
  * [ ] Documentation
    * [ ] Add click examples comparing PaulStretch to NessStretch.
    * [ ] Add time compression examples.
  * [ ] Send fan mail to Paul Nasca

* Longer term:
  * [ ] Make the audio formats more flexible.
  * [ ] Make the analysis settings more flexible.
    * [ ] Accept a CSV of fancy_bands (frequency, low bin, high bin) data.
    * [x] Add an option to turn off the phase scrambling.  (completed 20200801)

* Questions:
  * Why do the shorter-frame time stretches take so much longer (proportionally, almost) than the longer-frame ones?  It it just because there are so many frames to render?  Is there something simple we can do to speed them up?
