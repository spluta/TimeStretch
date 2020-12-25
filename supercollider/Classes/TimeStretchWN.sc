
TimeStretchWN {
	classvar synths;
	//by Sam Pluta - sampluta.com
	// Based on the Alex Ness's NessStretch algorithm in Python
	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code

	*initClass {
		synths = List.newClear(0);
		StartUp.add {

			SynthDef(\wn_monoStretch_Overlap2, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, wintype = 1, amp = 1, gate = 1, winExp = 1.2|
				var trigPeriod, sig, chain, chainA, chainB, trig, pos, jump, totFrames, trigEnv, fftDelay, paulEnv, winChoice, bigEnv, warp, noise;

				trigPeriod = (fftSize/SampleRate.ir);
				trig = Impulse.ar(1/trigPeriod);

				totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);

				startPos = (startPos%1);
				pos = Line.ar(startPos*BufFrames.kr(bufnum), BufFrames.kr(bufnum), BufDur.kr(bufnum)*stretch*(1-startPos));

				jump = fftSize/stretch/2;
				pos = [pos, pos + jump];

				paulEnv = 1-(Slew.ar(
					1-Trig1.ar(trig, fftSize/2/SampleRate.ir),
					SampleRate.ir/(fftSize/2),
					SampleRate.ir/(fftSize/2))**2);

				sig = PlayBuf.ar(1, bufnum, 1, trig, pos, 1)*SinOsc.ar(1/(2*trigPeriod)).abs;

				winChoice = Select.kr(wintype, [0, 0, -1]);

				noise = [WhiteNoise.ar(1), WhiteNoise.ar(1)];

				sig = sig.collect({ |item, i|
					chainA = FFT(LocalBuf(fftSize), item, 0.5, 0, 1, fftSize/2);
					chainB = FFT(LocalBuf(fftSize), noise[i], 0.5, -1, 1, fftSize/2);
					chain = PV_Mul(chainA, chainB);

					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass);
					item = IFFT(chain, wintype: 0);
				});

				warp = (SinOsc.ar(1/(2*trigPeriod))**(winExp-1)).abs;

				trigEnv = Select.ar(wintype,
					[warp, K2A.ar(1), paulEnv**1.25]);

				//fftDelay = fftSize-BlockSize.ir/SampleRate.ir;
				//trigEnv = DelayC.ar(trigEnv, fftDelay, fftDelay);
				//sig = sig*trigEnv;

				sig = DelayC.ar(sig*amp, fftMax/2-(fftSize/2)/SampleRate.ir, fftMax/2-(fftSize/2)/SampleRate.ir);

				sig[1] = DelayC.ar(sig[1], trigPeriod/2, trigPeriod/2);

				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
				Out.ar(out, Pan2.ar(Mix.new(sig), pan)/200*bigEnv);
			}).writeDefFile;

		}
	}

	*stretch { |inFile, outFile, durMult, fftMax = 65536, overlaps = 2, numSplits = 9, wintype = 0, winExp=1.1, amp = 1, action|
		var sf, argses, args, nrtJam, synthChoice, synths, numChans, server, buffer0, buffer1, filtVals, fftVals, fftBufs, headerFormat;

		action ?? {action = {"done stretchin!".postln}};

		if(overlaps.size==0){
			overlaps = Array.fill(numSplits, {overlaps})
		}{
			if(overlaps.size<numSplits){(numSplits-overlaps.size).do{overlaps = overlaps.add(overlaps.last)}}
		};

		if(wintype.size==0){
			wintype = Array.fill(numSplits, {wintype})
		}{
			if(wintype.size<numSplits){(numSplits-wintype.size).do{wintype = wintype.add(wintype.last)}}
		};

		if(winExp.size==0){
			winExp = Array.fill(numSplits, {winExp})
		}{
			if(winExp.size<numSplits){(numSplits-winExp.size).do{winExp = winExp.add(winExp.last)}}
		};

		inFile = PathName(inFile);
		if((inFile.extension=="wav")||(inFile.extension=="aif")||(inFile.extension=="aiff")){

			sf = SoundFile.openRead(inFile.fullPath);

			numChans = sf.numChannels;

			if(outFile == nil){outFile = inFile.pathOnly++inFile.fileNameWithoutExtension++durMult++".wav"};

			//Server.local.options.verbosity_(verbosity);

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

			if((numSplits-1)<8){ filtVals = filtVals.copyRange(0, (numSplits-1))};
			filtVals.put(filtVals.size-1, [filtVals[filtVals.size-1][0], 1]);

			fftVals = List.fill(filtVals.size, {|i| fftMax/(2**i)});

			buffer0 = Buffer.new(server, 0, 1);
			buffer1 = Buffer.new(server, 0, 1);

			nrtJam = Score.new();

			nrtJam = this.addBundles(nrtJam, server, inFile, buffer0, 0, durMult, overlaps, -1, amp, filtVals, fftVals, fftMax, wintype, winExp);
			if(numChans>1){
				nrtJam = this.addBundles(nrtJam, server, inFile, buffer1, 1, durMult, overlaps, 1, amp, filtVals, fftVals, fftMax, wintype, winExp);
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
				action: action
			);

		}{"Not an audio file!".postln;}
	}

	*addBundles {|nrtJam, server, inFile, buffer, chanNum, durMult, overlaps, pan, amp, filtVals, fftVals, fftMax, wintype, winExp|

		nrtJam.add([0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [chanNum])]);
		filtVals.do{|fv, i|
			switch(overlaps[i],
				2, {overlaps.put(i, 2)},
				{overlaps.put(i, 4)}
			);

			nrtJam.add([0.0, Synth.basicNew(("wn_monoStretch_Overlap"++overlaps[i]), server).newMsg(args: [bufnum: buffer.bufnum, pan: pan, fftSize:fftVals[i].postln, fftMax:fftMax, \stretch, durMult, \hiPass, fv[0], \lowPass, fv[1]-1, \wintype, wintype[i],\amp, amp, \winExp, winExp[i].postln])])
		};
		^nrtJam
	}

}