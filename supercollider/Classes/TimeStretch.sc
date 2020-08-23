NRT_Server_ID {
	classvar <id=5000;
	*initClass { id = 5000; }
	*next  { ^id = id + 1; }
	*path {this.filenameSymbol.postln}
}

TimeStretch {
	classvar synths;
	//by Sam Pluta - sampluta.com
	// Based on the Alex Ness's NessStretch algorith in Python
	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code

	*initClass {
		synths = List.newClear(0);
		StartUp.add {

			SynthDef(\pb_monoStretch_Overlap4, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, amp = 1, gate = 1|
				var trigPeriod, sig, chain, trig, pos, posB, stretchDur, jump, env, extraDel, bigEnv, count, totFrames, fftBufs, trigEnv;
				trigPeriod = (fftSize/SampleRate.ir);
				trigEnv = EnvGen.ar(Env([0,0,1], [1,0]), 1);
				trig = Impulse.ar(1/trigPeriod);

				totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);

				jump = trigPeriod/BufDur.kr(bufnum)/stretch/4;

				startPos = startPos%1;
				pos = Line.ar(startPos, 1, BufDur.kr(bufnum)*stretch*(1-startPos));

				pos = [pos, pos + jump, pos + (2*jump), pos + (3*jump)];

				sig = GrainBuf.ar(1, trig, trigPeriod, bufnum, 1, pos, envbufnum: -1);
				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
					chain = PV_Diffuser(chain, 1-trig);
					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass);
					item = IFFT(chain);
				});
				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);

				sig = DelayC.ar(sig*bigEnv*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);

				sig[1] = DelayC.ar(sig[1], trigPeriod/4, trigPeriod/4);
				sig[2] = DelayC.ar(sig[2], trigPeriod/2, trigPeriod/2);
				sig[3] = DelayC.ar(sig[3], 3*trigPeriod/4, 3*trigPeriod/4);
				Out.ar(out, Pan2.ar(Mix.new(sig), pan)*0.375);
			}).writeDefFile;

			SynthDef(\pb_monoStretch_Overlap3, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, amp = 1, gate = 1|
				var trigPeriod, sig, chain, trig, pos, posB, stretchDur, jump, env, extraDel, bigEnv, count, totFrames, fftBufs, trigEnv;
				trigPeriod = (fftSize/SampleRate.ir);
				trigEnv = EnvGen.ar(Env([0,0,1], [1,0]), 1);
				trig = Impulse.ar(1/trigPeriod);

				totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);

				jump = trigPeriod/BufDur.kr(bufnum)/stretch/3;

				startPos = startPos%1;
				pos = Line.ar(startPos, 1, BufDur.kr(bufnum)*stretch*(1-startPos));

				pos = [pos, pos + jump, pos + (2*jump)];

				sig = GrainBuf.ar(1, trig, trigPeriod, bufnum, 1, pos, envbufnum: -1);
				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
					chain = PV_Diffuser(chain, 1-trig);
					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass);
					item = IFFT(chain);
				});
				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);

				sig = DelayC.ar(sig*bigEnv*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);

				sig[1] = DelayC.ar(sig[1], trigPeriod/3, trigPeriod/3);
				sig[2] = DelayC.ar(sig[2], 2*trigPeriod/3, 2*trigPeriod/3);
				Out.ar(out, Pan2.ar(Mix.new(sig), pan)*0.46875);
			}).writeDefFile;

			SynthDef(\pb_monoStretch_Overlap2, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, amp = 1, gate = 1|
				var trigPeriod, sig, chain, trig, pos, posB, stretchDur, jump, env, extraDel, bigEnv, count, totFrames, fftBufs, trigEnv;
				trigPeriod = (fftSize/SampleRate.ir);
				trigEnv = EnvGen.ar(Env([0,0,1], [1,0]), 1);
				trig = Impulse.ar(1/trigPeriod);

				totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);

				jump = trigPeriod/BufDur.kr(bufnum)/stretch/2;
				startPos = startPos%1;
				pos = Line.ar(startPos, 1, BufDur.kr(bufnum)*stretch*(1-startPos));

				pos = [pos, pos + jump];

				sig = GrainBuf.ar(1, trig, trigPeriod, bufnum, 1, pos, envbufnum: -1);
				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
					chain = PV_Diffuser(chain, 1-trig);
					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass);
					item = IFFT(chain, wintype: -1);
				});
				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);

				trigEnv = 1-(Slew.ar(
					1-Trig1.ar(trig, fftSize/2/SampleRate.ir),
					SampleRate.ir/(fftSize/2),
					SampleRate.ir/(fftSize/2))**2)**1.25;

				sig = DelayC.ar(sig*bigEnv*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);
				trigEnv = DelayC.ar(trigEnv, fftMax-fftSize-BlockSize.ir/SampleRate.ir, fftMax-fftSize-BlockSize.ir/SampleRate.ir);
				sig = sig*trigEnv;

				sig[1] = DelayC.ar(sig[1], trigPeriod/2, trigPeriod/2);
				Out.ar(out, Pan2.ar(Mix.new(sig), pan)/2);
			}).writeDefFile;

		}
	}

	*stretchNRT { |inFile, outFile, durMult, fftMax = 65536, overlaps = 2, numSplits = 9, amp = 1|
		var sf, argses, args, nrtJam, synthChoice, synths, numChans, server, buffer0, buffer1, filtVals, fftVals, fftBufs, headerFormat;

		if(overlaps.size==0){
			overlaps = Array.fill(numSplits, {overlaps})
		}{
			if(overlaps.size<numSplits){(numSplits-overlaps.size).do{overlaps = overlaps.add(overlaps.last)}}
		};

		overlaps.postln;

		inFile = PathName(inFile);
		if((inFile.extension=="wav")||(inFile.extension=="aif")){
			"NRT Ness Stretch".postln;

			sf = SoundFile.openRead(inFile.fullPath);

			numChans = sf.numChannels;

			if(outFile == nil){outFile = PathName(inFile).pathOnly++PathName(outFile).fileNameWithoutExtension++durMult++".wav"};

			server = Server(("nrt"++NRT_Server_ID.next).asSymbol,
				options: Server.local.options
				.numOutputBusChannels_(numChans)
				.numInputBusChannels_(numChans)
			);

			if(sf.sampleRate<50000){
				filtVals = List.fill(8, {|i| 1/2**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
			}{
				filtVals = List.fill(8, {|i| 1/4**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
			};
			if(sf.sampleRate>100000){
				filtVals = List.fill(8, {|i| 1/8**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
			};
			filtVals.postln;

			if((numSplits-1)<8){ filtVals = filtVals.copyRange(0, (numSplits-1))};
			filtVals.put(filtVals.size-1, [filtVals[filtVals.size-1][0], 1]);

			fftVals = List.fill(filtVals.size, {|i| fftMax/(2**i)});


			buffer0 = Buffer.new(server, 0, 1);
			buffer1 = Buffer.new(server, 0, 1);

			nrtJam = Score.new();

			nrtJam = this.addBundles(nrtJam, server, inFile, buffer0, 0, durMult, overlaps, -1, amp, filtVals, fftVals, fftMax);
			if(numChans>1){
				nrtJam = this.addBundles(nrtJam, server, inFile, buffer1, 1, durMult, overlaps, 1, amp, filtVals, fftVals, fftMax);
			};

			if((sf.duration*sf.numChannels*durMult)<(8*60*60)){headerFormat="wav"}{
				headerFormat="caf";
				outFile = PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++".caf";
			};

			nrtJam.recordNRT(
				outputFilePath: outFile.standardizePath,
				sampleRate: sf.sampleRate,
				headerFormat: headerFormat,
				sampleFormat: "int24",
				options: server.options,
				duration: sf.duration*durMult+3,
				action: {"done stretchin!".postln}
			);

		}{"Not an audio file!".postln;}
	}

	*addBundles {|nrtJam, server, inFile, buffer, chanNum, durMult, overlaps, pan, amp, filtVals, fftVals, fftMax|

		nrtJam.add([0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [chanNum])]);
		filtVals.postln;
		overlaps.postln;
		filtVals.do{|fv, i|
			switch(overlaps[i],
				2, {overlaps.put(i, 2)},
				3, {overlaps.put(i, 3)},
				{overlaps.put(i, 4)}
			);

			nrtJam.add([0.0, Synth.basicNew(("pb_monoStretch_Overlap"++overlaps[i]).postln, server).newMsg(args: [bufnum: buffer.bufnum, pan: pan, fftSize:fftVals[i], fftMax:fftMax, \stretch, durMult, \hiPass, fv[0], \lowPass, fv[1]-1, \amp, amp])])
		};
		^nrtJam
	}

	*stretchRT1 { |target, bufferChan, outBus=0, pan=0, durMult=10, overlaps=4, startPos = 0, fftSize = 8192, amp = 1|

		switch(overlaps,
			2, {overlaps=2},
			3, {overlaps=3},
			{overlaps=4}
		);

		synths.add(Synth.new("pb_monoStretch_Overlap"++overlaps, [\out, outBus, \bufnum, bufferChan, \pan, pan, \stretch, durMult, \startPos, startPos, \fftSize, fftSize, \fftMax, fftSize, \amp, amp, \gate, 1], target));
	}

	*stretchRT { |target, bufferChan, outBus=0, pan=0, durMult=10, overlaps=4, startPos = 0, fftMax = 32768, numSplits = 4, amp = 1|
		var filtVals, fftVals;

		switch(overlaps,
			2, {overlaps=2},
			3, {overlaps=3},
			{overlaps=4}
		);

		filtVals = List.fill(8, {|i| 1/2**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);

		if((numSplits-1)<8){ filtVals = filtVals.copyRange(0, (numSplits-1))};
		filtVals.put(filtVals.size-1, [filtVals[filtVals.size-1][0], 1]);

		fftVals = List.fill(filtVals.size, {|i| fftMax/(2**i)});

		filtVals.do{|fv, i|
			synths.add(Synth.new("pb_monoStretch_Overlap"++overlaps, [\out, outBus, \bufnum, bufferChan, \pan, pan, \stretch, durMult, \hiPass, fv[0], \lowPass, fv[1]-1, \startPos, startPos, \fftSize, fftVals[i], \fftMax, fftMax, \amp, amp, \gate, 1], target));
		}
	}

	*stop {
		synths.do{|synth| if(synth!=nil){synth.set(\gate, 0)}};
		synths = List.newClear(0);
	}
}