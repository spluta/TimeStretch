TimeStretch2 {
	//by Sam Pluta - sampluta.com
	// Based on the Alex Ness's NessStretch algorithm in Python
	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code

	*initClass {
		StartUp.add {
			SynthDef(\pb_monoStretch2_Overlap2, { |out = 0, bufnum, stretch = 100, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, amp = 1, gate = 1|
				var trigPeriod, sig, chain, trig, trig1, trig2, pos, jump, trigEnv, fftDelay, bigEnv, window0, window1, rVal, correlation, sum, localIn, rVal1, rVal2, outSig, analSig;

				trigPeriod = (fftSize/SampleRate.ir);
				trig = Impulse.ar(2/trigPeriod);

				trig1 = PulseDivider.ar(trig, 2, 1);
				trig2 = PulseDivider.ar(trig, 2, 0);

				startPos = (startPos%1);
				pos = Line.ar(startPos*BufFrames.kr(bufnum), BufFrames.kr(bufnum), BufDur.kr(bufnum)*stretch*(1-startPos));

				jump = fftSize/stretch/2;
				pos = [pos, pos + jump];

				sig = PlayBuf.ar(1, bufnum, 1, trig1, pos, 1)*SinOsc.ar(1/(2*trigPeriod)).abs*0.5;

				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: 0);
					chain = PV_Diffuser(chain, 1-trig1);
					chain = PV_BrickWall(chain, hiPass);
					chain = PV_BrickWall(chain, lowPass-1);
					item = IFFT(chain, wintype: -1);
				}).flatten;

				//delay the signal so that all fftSizes line up (the will already be delayed by the fftSize
				sig = DelayC.ar(sig, fftMax-fftSize+BlockSize.ir/SampleRate.ir, fftMax-fftSize+BlockSize.ir/SampleRate.ir);
				/*
				analSig = [sig[1],sig[3]];
				sig = [sig[0], sig[2]];*/

				//offset the second channel of frames
				//analSig[1] = DelayC.ar(analSig[1], trigPeriod/2, trigPeriod/2);
				sig[1] = DelayC.ar(sig[1], trigPeriod/2, trigPeriod/2);

				//calculate the average correlation over each
				//correlation = ((sig[0]*sig[1])/((sig[0]*sig[0]).clip(0.001, 1))).clip(-1.0, 1.0);

				//sum = RunningSum.ar((analSig[0]*analSig[1]), fftSize/2)/RunningSum.ar((analSig[0]*analSig[0]), fftSize/2);
				sum = RunningSum.ar((sig[0]*sig[1]), fftSize/2)/RunningSum.ar((sig[0]*sig[0]), fftSize/2);

				rVal = Latch.ar(sum, trig1+trig2).clip(-1,1);

				rVal = DelayC.ar(rVal, trigPeriod/2, trigPeriod/2);

				localIn = LocalIn.ar(1).clip(-1,1);
				localIn = DelayC.ar(localIn, trigPeriod/2-(BlockSize.ir/SampleRate.ir), trigPeriod/2-(BlockSize.ir/SampleRate.ir));

				rVal1 = (Latch.ar(rVal, trig1)>=0).linlin(0,1,-1,1)*
				Latch.ar(XFade2.ar(K2A.ar(1), localIn, EnvGen.kr(Env([-1,-1, 1], [trigPeriod, 0]), 1)), trig1);

				rVal2 = (Latch.ar(rVal, trig2)>=0).linlin(0,1,-1,1)*
				Latch.ar(XFade2.ar(K2A.ar(1), DelayC.ar(rVal1.clip(-1,1), trigPeriod/2, trigPeriod/2), EnvGen.kr(Env([-1,-1, 1], [trigPeriod, 0]), 1)), trig2);

				LocalOut.ar(rVal2);

				window0 = NessWindow.ar(trig1, rVal.abs, fftSize)*rVal1;
				window1 = NessWindow.ar(trig2, rVal.abs, fftSize)*rVal2;

				//window0 = (SinOsc.ar(1/(2*trigPeriod))**((rVal.abs))).abs*rVal1;
				//window1 = (SinOsc.ar(1/(2*trigPeriod), pi/2)**((rVal.abs))).abs*rVal2;

				sig = DelayC.ar(sig, fftMax/SampleRate.ir, fftMax/SampleRate.ir);

				outSig = [sig[0]*window0, sig[1]*window1];

				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);

				hiPass = hiPass*SampleRate.ir/2;
				lowPass = lowPass*SampleRate.ir/2;

				outSig = HPF.ar(HPF.ar(outSig, (hiPass).clip(20, SampleRate.ir/2)), (hiPass).clip(20, SampleRate.ir/2));
				outSig = LPF.ar(LPF.ar(outSig, (lowPass).clip(20, SampleRate.ir/2)), (lowPass).clip(20, SampleRate.ir/2));


				Out.ar(out, Mix.new(outSig)*bigEnv*amp);

				//Out.ar(out, [sig[0], sig[1], rVal, window0, window1])
			}).writeDefFile;

		}
	}

	*stretch { |inFile, outFile, durMult, fftMax = 65536, numSplits = 9, amp = 1, action|
		var sf, argses, args, nrtJam, synthChoice, synths, numChans, server, buffer0, buffer1, filtVals, fftVals, fftBufs, headerFormat;

		action ?? {action = {"done stretchin!".postln}};

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

			nrtJam = this.addBundles(nrtJam, server, inFile, buffer0, 0, durMult, 0, amp, filtVals, fftVals, fftMax);
			if(numChans>1){
				nrtJam = this.addBundles(nrtJam, server, inFile, buffer1, 1, durMult, 1, amp, filtVals, fftVals, fftMax);
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

	*addBundles {|nrtJam, server, inFile, buffer, chanNum, durMult, outChan, amp, filtVals, fftVals, fftMax|

		nrtJam.add([0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [chanNum])]);
		filtVals.do{|fv, i|
			//nrtJam.add([0.0, Synth.basicNew(("pb_monoStretch2_Overlap_"++fftVals[i].asInteger).asSymbol.postln, server).newMsg(args: [\out, outChan, \bufnum, buffer.bufnum, fftSize:fftVals[i].postln, fftMax:fftMax, \stretch, durMult, \hiPass, fv[0].postln, \lowPass, (fv[1]).postln, \amp, amp])])
			nrtJam.add([0.0, Synth.basicNew((\pb_monoStretch2_Overlap2), server).newMsg(args: [\out, outChan, \bufnum, buffer.bufnum, fftSize:fftVals[i].postln, fftMax:fftMax, \stretch, durMult, \hiPass, fv[0].postln, \lowPass, (fv[1]).postln, \amp, amp])])
		};
		^nrtJam
	}




}



// TimeStretch2 {
// 	classvar synths;
// 	//by Sam Pluta - sampluta.com
// 	// Based on the Alex Ness's NessStretch algorithm in Python
// 	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code
//
// 	*initClass {
// 		synths = List.newClear(0);
// 		StartUp.add {
//
// 			SynthDef(\monoStretch_Overlap0, { |out = 0, bufnum, pan = 0, stretch = 100, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, wintype = 1, amp = 1, gate = 1, winExp = 1.2|
// 				var trigPeriod, sig, chain, trig, pos, jump, trigEnv, fftDelay, paulEnv, winChoice, bigEnv, warp;
//
// 				trigPeriod = (fftSize/SampleRate.ir);
// 				trig = Impulse.ar(1/trigPeriod);
//
// 				startPos = (startPos%1);
// 				pos = Line.ar(startPos*BufFrames.kr(bufnum), BufFrames.kr(bufnum), BufDur.kr(bufnum)*stretch*(1-startPos)*2);
//
// 				sig = PlayBuf.ar(1, bufnum, 1, trig, pos, 1)*SinOsc.ar(1/(2*trigPeriod)).abs;
//
// 				chain = FFT(LocalBuf(fftSize), sig, hop: 1, wintype: 0);
// 				chain = PV_Diffuser(chain, 1-trig);
// 				chain = PV_BrickWall(chain, hiPass);
// 				chain = PV_BrickWall(chain, lowPass);
// 				sig = IFFT(chain, wintype: -1);
//
// 				sig = DelayC.ar(sig*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);
//
// 				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
// 				Out.ar(sig, Pan2.ar(Mix.new(sig), pan)/2*bigEnv);
// 			}).writeDefFile;
//
// 		}
// 	}
//
// 	*stretch { |inFile, outFile, durMult, fftMax = 65536, overlaps = 2, numSplits, anaylysis = 1, wintype = 0, winExp=1.1, amp = 1, action|
// 		var sf, argses, args, nrtJam, synthChoice, synths, numChans, server, buffer0, buffer1, filtVals, fftVals, fftBufs, headerFormat, tempFiles;
//
// 		action ?? {action = {"done stretchin!".postln}};
//
// 		if(anaylysis.size==0){
// 			anaylysis = Array.fill(numSplits, {anaylysis})
// 		}{
// 			if(anaylysis.size<numSplits){(numSplits-anaylysis.size).do{anaylysis = anaylysis.add(anaylysis.last)}}
// 		};
//
// 		if(overlaps.size==0){
// 			overlaps = Array.fill(numSplits, {overlaps})
// 		}{
// 			if(overlaps.size<numSplits){(numSplits-overlaps.size).do{overlaps = overlaps.add(overlaps.last)}}
// 		};
//
// 		if(wintype.size==0){
// 			wintype = Array.fill(numSplits, {wintype})
// 		}{
// 			if(wintype.size<numSplits){(numSplits-wintype.size).do{wintype = wintype.add(wintype.last)}}
// 		};
//
// 		if(winExp.size==0){
// 			winExp = Array.fill(numSplits, {winExp})
// 		}{
// 			if(winExp.size<numSplits){(numSplits-winExp.size).do{winExp = winExp.add(winExp.last)}}
// 		};
//
// 		inFile = PathName(inFile);
// 		if((inFile.extension=="wav")||(inFile.extension=="aif")||(inFile.extension=="aiff")){
//
// 			sf = SoundFile.openRead(inFile.fullPath);
//
// 			numChans = sf.numChannels;
//
// 			if(outFile == nil){outFile = inFile.pathOnly++inFile.fileNameWithoutExtension++"_"++durMult};
//
// 			//Server.local.options.verbosity_(verbosity);
//
// 			if(sf.sampleRate<50000){
// 				filtVals = List.fill(8, {|i| 1/2**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
// 			}{
// 				filtVals = List.fill(8, {|i| 1/4**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
// 			};
// 			if(sf.sampleRate>100000){
// 				filtVals = List.fill(8, {|i| 1/8**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
// 			};
//
// 			if((numSplits-1)<8){ filtVals = filtVals.copyRange(0, (numSplits-1))};
// 			filtVals.put(filtVals.size-1, [filtVals[filtVals.size-1][0], 1]);
//
// 			fftVals = List.fill(filtVals.size, {|i| fftMax/(2**i)});
//
// 			numSplits.do{|split, i|
// 				this.stretchAndAnalyze(inFile, outFile, i, durMult, filtVals[i], fftVals[i], fftMax);
// 			};
//
// 		}{"Not an audio file!".postln;}
// 	}
//
//
// 	stretchAndAnalyse {|inFile, outFile, split, durMult, filtVal, fftVal, fftMax|
// 		var buffer0, buffer1;
//
// 		server = Server(("nrt"++NRT_Server_ID.next).asSymbol,
// 			options: Server.local.options
// 			.numOutputBusChannels_(numChans)
// 			.numInputBusChannels_(numChans)
// 		);
//
// 		buffer0 = Buffer.new(server, 0, 1);
// 		buffer1 = Buffer.new(server, 0, 1);
//
// 		nrtJam = Score.new();
//
// 		nrtJam = this.addBundles(nrtJam, server, inFile, buffer0, 0, durMult, -1, amp, filtVal, fftVal, fftMax);
// 		if(numChans>1){
// 			nrtJam = this.addBundles(nrtJam, server, inFile, buffer1, 1, durMult, 1, amp, filtVal, fftVal, fftMax);
// 		};
//
// 		if((sf.duration*sf.numChannels*durMult*2)<(8*60*60))
// 		{
// 			headerFormat="wav"
// 			outFile = PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++"_temp_"++split++".wav"
// 		}{
// 			headerFormat="caf";
// 			outFile = PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++"_temp_"++split++".caf";
// 		};
//
//
// 		nrtJam.recordNRT(
// 			outputFilePath: outFile.standardizePath,
// 			sampleRate: sf.sampleRate,
// 			headerFormat: headerFormat,
// 			sampleFormat: "int24",
// 			options: server.options,
// 			duration: (sf.numFrames*durMult*2+(fftMax-server.options.blockSize))/sf.sampleRate,
// 			action: {
// 				this.getCorrelation(outFile, fftMax, fftVal, 0);
// 				this.getCorrelation(outFile, fftMax, fftVal, 0);
// 			}
// 		);
// 	}
//
//
// 	*addBundles {|nrtJam, server, inFile, buffer, chanNum, durMult, pan, amp, filtVal, fftVal, fftMax|
//
// 		nrtJam.add([0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [chanNum])]);
// 		fftVal.postln;
// 		nrtJam.add([0.0, Synth.basicNew(("\monoStretch_Overlap0"), server).newMsg(args: [bufnum: buffer.bufnum, pan: pan, fftSize:fftVals[split].postln, fftMax:fftMax, \stretch, durMult, \hiPass, filtVals[split][0], \lowPass, filtVals[split][1]-1, \wintype, wintype[split],\amp, amp, \winExp, winExp[split].postln])])
// 		^nrtJam
// 	}
//
// 	*getCorrelation {|fileName, fftMax = 65536, windowSize=8192, chanNum=0, action;
// 		var arrayA, arrayB, correlations, getNext, sf, numFrames;
//
// 		windowSize = (fftMax/(2**num)).asInteger;
//
// 		sf = SoundFile.openRead(fileName);
// 		numFrames = sf.numFrames-(windowSize-64)/windowSize;
//
// 		getNext = {|counter, countTo, arrayA|
// 			counter.postln;
// 			if(counter<=countTo){
// 				Buffer.readChannel(s, fileName, (fftMax-64)+(counter*windowSize), windowSize, [chanNum], {|bufB|
// 					bufB.loadToFloatArray(action:{|arrayB|
// 						var smallArrays;
//
// 						smallArrays = [arrayA.copyRange((windowSize/2).asInteger, windowSize-1), arrayB.copyRange(0, (windowSize/2).asInteger-1)];
//
// 						correlations.put(counter-1, (((smallArrays[0]*smallArrays[1]).sum)/((smallArrays[0]*smallArrays[0]).sum)));
// 						bufB.free;
// 						getNext.value(counter+1, countTo, arrayB)
// 					})
// 				});
// 			}{
// 				"correlations calculated!".postln;
// 				"writing file".postln;
// 				correlations.writeArchive(PathName(fileName).pathOnly++PathName(fileName).fileNameWithoutExtension++"_chan"++chanNum++".analysis");
// 			}
// 		};
//
//
// 		correlations = List.newClear(numFrames-1);
//
// 		"calculating correlations".postln;
// 		Buffer.readChannel(s, fileName, (fftMax-64), windowSize, [chanNum], {|bufA|
// 			bufA.loadToFloatArray(action:{|arrayA|
// 				bufA.free;
// 				getNext.value(1, numFrames-1, arrayA);
//
// 			})
// 		})
// 	}
//
//
// }