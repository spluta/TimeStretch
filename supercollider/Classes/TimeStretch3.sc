TimeStretch3 {
	classvar synths;
	//by Sam Pluta - sampluta.com
	// Based on the Alex Ness's NessStretch algorithm in Python
	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code

	*initClass {
		synths = List.newClear(0);
		StartUp.add {
			Array.fill(9, {|i| 256*(2**i)}).do{|fftSize, i|
				var lowBin, highBin;
				if(i==0)
				{lowBin=32; highBin = 127}
				{lowBin=64; highBin = 127};

				SynthDef(("timeStretch3_"++fftSize.asInteger).asSymbol, { |in=0, out = 0, fftSize, delay, hiPass = 0, lowPass=0, amp = 1, gate = 1|
					var sig, chain, bigEnv, trig, trigPeriod;

					trigPeriod = (fftSize/SampleRate.ir);
					trig = Impulse.ar(1/trigPeriod);

					sig = DelayC.ar(SoundIn.ar(in), 65536/SampleRate.ir, delay);

					//phases = Array.fill(highBin-lowBin, {TRand.ar(-pi, pi, trig1)});

					chain = FFT(LocalBuf(fftSize), sig, hop: 1.0, wintype: 0/*, active: ToggleFF.ar(trig)*/);
					chain = PV_Diffuser(chain, 1-trig);
					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass-1);
					sig = IFFT(chain, wintype: -1);

					sig = LeakDC.ar(sig);

					bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);

					hiPass = hiPass*SampleRate.ir/2;
					lowPass = lowPass*SampleRate.ir/2;

					sig = HPF.ar(HPF.ar(sig, (hiPass).clip(20, SampleRate.ir/2)), (hiPass).clip(20, SampleRate.ir/2));
					sig = LPF.ar(LPF.ar(sig, (lowPass).clip(20, SampleRate.ir/2)), (lowPass).clip(20, SampleRate.ir/2));

					sig = DelayC.ar(sig, (65536-fftSize+64)/SampleRate.ir, (65536-fftSize+64)/SampleRate.ir);

					Out.ar(out, sig);

				}).writeDefFile;
			};
		}
	}

	*stretch { |inFile, outFile, durMult, fftMax = 65536, numSplits = 9, amp = 1, action|
		var sf, argses, args, synthChoice, synths, numChans, filtVals, fftVals, fftBufs, headerFormat, tempDir;

		action ?? {action = {"done stretchin!".postln}};

		inFile = PathName(inFile);
		if((inFile.extension=="wav")||(inFile.extension=="aif")||(inFile.extension=="aiff")){

			sf = SoundFile.openRead(inFile.fullPath);

			numChans = sf.numChannels;

			tempDir = (PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++"_render/").standardizePath;
			if(PathName(tempDir).isFolder.not){("mkdir "++tempDir.escapeChar($ )).systemCmd};

			if(outFile == nil){outFile = inFile.pathOnly++inFile.fileNameWithoutExtension++durMult};


			if(sf.sampleRate<50000){
				filtVals = List.fill(8, {|i| 1/2**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
			}{
				filtVals = List.fill(8, {|i| 1/4**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
			};
			if(sf.sampleRate>100000){
				filtVals = List.fill(8, {|i| 1/8**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
			};

			if((numSplits-1)<8){ filtVals = filtVals.copyRange(0, (numSplits-1))};
			filtVals.put(filtVals.size-1, [filtVals[filtVals.size-1][0], 1]);

			fftVals = List.fill(filtVals.size, {|i| fftMax/(2**i)});

			numChans.do{|chan|
				filtVals.do{|fv, i|
					var server, outFileLocal, nrtJam, samples;

					server = Server(("nrt"++NRT_Server_ID.next).asSymbol,
						options: Server.local.options
						.numOutputBusChannels_(durMult*2)
						.numInputBusChannels_(2)
					);

					nrtJam = Score.new();

					nrtJam = this.addBundles(nrtJam, server, chan, durMult, 0, amp, fv, fftVals[i], fftMax, sf);

					outFileLocal = tempDir++PathName(outFile).fileNameWithoutExtension++"_chan"++chan++"_"++i++".caf";
					headerFormat="caf";

					samples = (sf.numFrames/fftMax+1).ceil*fftMax;

					nrtJam.recordNRT(
						outputFilePath: outFileLocal.standardizePath,
						inputFilePath: inFile.fullPath,
						sampleRate: sf.sampleRate,
						headerFormat: headerFormat,
						sampleFormat: "int24",
						options: server.options,
						duration: samples/sf.sampleRate,
						action: action
					);
				}
			}

		}{"Not an audio file!".postln;}
	}

	*addBundles {|nrtJam, server, chanNum, durMult, outChan, amp, fv, fftVal, fftMax, sf|

		(durMult*2).do{|num|
			var delay;
			delay = fftVal*((num)/(durMult*2))/sf.sampleRate;
			delay.postln;
			nrtJam.add([0.0, Synth.basicNew(("timeStretch3_"++fftVal.asInteger).asSymbol, server).newMsg(args: [\in, chanNum, \out, num*(outChan+1), fftSize:fftVal.postln, \delay, delay, \hiPass, fv[0], \lowPass, (fv[1]), \amp, amp])]);
		};
		^nrtJam
	}



}

