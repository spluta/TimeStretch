NRT_Server_ID {
	classvar <id=5000;
	*initClass { id = 5000; }
	*next  { ^id = id + 1; }
	*path {this.filenameSymbol.postln}
}

TimeStretch {
	classvar synths;
	//by Sam Pluta - sampluta.com
	// Based on the Alex Ness's NessStretch algorithm in Python
	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code

	*initClass {
		synths = List.newClear(0);
		StartUp.add {

			SynthDef(\pb_monoStretch_Overlap4, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, amp = 1, gate = 1|
				var trigPeriod, sig, chain, trig, pos, jump, totFrames, trigEnv, bigEnv;

				trigPeriod = (fftSize/SampleRate.ir);
				trig = Impulse.ar(1/trigPeriod);

				totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);

				jump = fftSize/stretch/4;

				startPos = (startPos%1);
				pos = Line.ar(startPos*BufFrames.kr(bufnum), BufFrames.kr(bufnum), BufDur.kr(bufnum)*stretch*(1-startPos));

				pos = [pos, pos + jump, pos + (2*jump), pos + (3*jump)];

				sig = PlayBuf.ar(1, bufnum, 1, trig, pos, 1)*SinOsc.ar(1/(2*trigPeriod)).abs;

				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: 0);
					chain = PV_Diffuser(chain, 1-trig);
					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass);
					item = IFFT(chain, 0);
				});

				sig = DelayC.ar(sig*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);

				sig[1] = DelayC.ar(sig[1], trigPeriod/4, trigPeriod/4);
				sig[2] = DelayC.ar(sig[2], trigPeriod/2, trigPeriod/2);
				sig[3] = DelayC.ar(sig[3], 3*trigPeriod/4, 3*trigPeriod/4);

				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
				Out.ar(out, Pan2.ar(Mix.new(sig), pan)*0.5*bigEnv);
			}).writeDefFile;

			SynthDef(\pb_monoStretch_Overlap2, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, wintype = 1, amp = 1, gate = 1, sinePower = 1.1|
				var trigPeriod, sig, chain, trig, pos, jump, totFrames, trigEnv, fftDelay, paulEnv, winChoice, bigEnv, warp;

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

				winChoice = Select.kr(wintype, [0, -1, 0]);

				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 0.5, wintype: 0);
					chain = PV_Diffuser(chain, 1-trig);
					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass);
					item = IFFT(chain, wintype: winChoice);
				});

				warp = (SinOsc.ar(1/(2*trigPeriod))**(sinePower-1)).abs;

				trigEnv = Select.ar(wintype,
					[K2A.ar(1), paulEnv**1.25, warp]);

				fftDelay = fftSize-BlockSize.ir/SampleRate.ir;
				trigEnv = DelayC.ar(trigEnv, fftDelay, fftDelay);
				sig = sig*trigEnv;

				sig = DelayC.ar(sig*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);

				sig[1] = DelayC.ar(sig[1], trigPeriod/2, trigPeriod/2);

				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
				Out.ar(out, Pan2.ar(Mix.new(sig), pan)/2*bigEnv);
			}).writeDefFile;

		}
	}

	*stretch { |inFile, outFile, durMult, fftMax = 65536, overlaps = 2, numSplits = 9, wintype = 0, sinePower=1.1, amp = 1, action|
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

			nrtJam = this.addBundles(nrtJam, server, inFile, buffer0, 0, durMult, overlaps, -1, amp, filtVals, fftVals, fftMax, wintype, sinePower);
			if(numChans>1){
				nrtJam = this.addBundles(nrtJam, server, inFile, buffer1, 1, durMult, overlaps, 1, amp, filtVals, fftVals, fftMax, wintype, sinePower);
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

	*addBundles {|nrtJam, server, inFile, buffer, chanNum, durMult, overlaps, pan, amp, filtVals, fftVals, fftMax, wintype, sinePower|

		nrtJam.add([0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [chanNum])]);
		filtVals.do{|fv, i|
			switch(overlaps[i],
				2, {overlaps.put(i, 2)},
				{overlaps.put(i, 4)}
			);

			nrtJam.add([0.0, Synth.basicNew(("pb_monoStretch_Overlap"++overlaps[i]), server).newMsg(args: [bufnum: buffer.bufnum, pan: pan, fftSize:fftVals[i], fftMax:fftMax, \stretch, durMult, \hiPass, fv[0], \lowPass, fv[1]-1, \wintype, wintype[i],\amp, amp, \sinePower, sinePower])])
		};
		^nrtJam
	}

}