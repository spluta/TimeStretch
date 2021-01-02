//
//
// TimeStretch3 {
// 	classvar synths;
// 	//by Sam Pluta - sampluta.com
// 	// Based on the Alex Ness's NessStretch algorithm in Python
// 	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code
//
// 	*initClass {
// 		synths = List.newClear(0);
// 		StartUp.add {
// 			Array.fill(9, {|i| 256*(2**i)}).do{|fftSize, i|
// 				var lowBin, highBin;
// 				if(i==0)
// 				{lowBin=32; highBin = 127}
// 				{lowBin=64; highBin = 127};
//
// 				SynthDef(("timeStretch3_"++fftSize.asInteger).asSymbol, { |out = 0, bufnum, stretch = 100, startPos = 0, fftMax = 65536, hiPass = 0, lowPass=0, amp = 1, gate = 1|
// 					var trigPeriod, sig, chain, trig, trig1, trig2, pos, jump, trigEnv, fftDelay, bigEnv, window0, window1, rVal, correlation, sum, localIn, rVal1, rVal2, outSig, analSig, phases;
//
// 					trigPeriod = (fftSize/SampleRate.ir);
// 					trig = Impulse.ar(2/trigPeriod);
//
// 					trig1 = PulseDivider.ar(trig, 2, 1);
// 					trig2 = PulseDivider.ar(trig, 2, 0);
//
// 					startPos = (startPos%1);
// 					pos = Line.ar(startPos*BufFrames.kr(bufnum), BufFrames.kr(bufnum), BufDur.kr(bufnum)*stretch*(1-startPos));
//
// 					jump = fftSize/stretch/2;
// 					pos = [pos, pos + jump];
//
// 					sig = PlayBuf.ar(1, bufnum, 1, trig1, pos, 1)*PaulWindow.ar(trig1, 0, fftSize);
//
// 					//phases = Array.fill(highBin-lowBin, {TRand.ar(-pi, pi, trig1)});
//
// 					sig = sig.collect({ |item, i|
// 						chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: 0);
// 						chain = chain.pvcollect(fftSize, {|mag, phase, bin, index| [mag, TRand.kr(-pi, pi, trig1)]}, lowBin, highBin, 1);
// 						item = IFFT(chain, wintype: -1);
// 					}).flatten;
//
//
// 					sig = LeakDC.ar(sig);
// 					//delay the signal so that all fftSizes line up (the will already be delayed by the fftSize
// 					sig = DelayC.ar(sig, fftMax-fftSize+BlockSize.ir/SampleRate.ir, fftMax-fftSize+BlockSize.ir/SampleRate.ir);
//
// 					sig[1] = DelayC.ar(sig[1], trigPeriod/2, trigPeriod/2);
//
// 					bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
//
// 					hiPass = hiPass*SampleRate.ir/2;
// 					lowPass = lowPass*SampleRate.ir/2;
//
// 					outSig = HPF.ar(HPF.ar(sig, (hiPass).clip(20, SampleRate.ir/2)), (hiPass).clip(20, SampleRate.ir/2));
// 					outSig = LPF.ar(LPF.ar(sig, (lowPass).clip(20, SampleRate.ir/2)), (lowPass).clip(20, SampleRate.ir/2));
//
// 					Out.ar(out, outSig);
//
// 					//Out.ar(out, [sig[0], sig[1], rVal, window0, window1])
// 				}).writeDefFile;
// 			};
//
//
//
//
// 		}
// 	}
//
// 	*stretch { |inFile, outFile, durMult, fftMax = 65536, numSplits = 9, amp = 1, action|
// 		var sf, argses, args, synthChoice, synths, numChans, filtVals, fftVals, fftBufs, headerFormat;
//
// 		action ?? {action = {"done stretchin!".postln}};
//
// 		inFile = PathName(inFile);
// 		if((inFile.extension=="wav")||(inFile.extension=="aif")||(inFile.extension=="aiff")){
//
// 			sf = SoundFile.openRead(inFile.fullPath);
//
// 			numChans = sf.numChannels;
//
// 			if(outFile == nil){outFile = inFile.pathOnly++inFile.fileNameWithoutExtension++durMult++".wav"};
//
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
// 			filtVals.do{|fv, i|
// 				var server, buffer0, buffer1, nrtJam, outFileLocal;
//
// 				server = Server(("nrt"++NRT_Server_ID.next).asSymbol,
// 					options: Server.local.options
// 					.numOutputBusChannels_(numChans*2)
// 					.numInputBusChannels_(numChans*2)
// 				);
//
// 				buffer0 = Buffer.new(server, 0, 1);
// 				buffer1 = Buffer.new(server, 0, 1);
//
// 				nrtJam = Score.new();
//
// 				nrtJam = this.addBundles(nrtJam, server, inFile, buffer0, 0, durMult, 0, amp, fv, fftVals[i], fftMax);
// 				if(numChans>1){
// 					nrtJam = this.addBundles(nrtJam, server, inFile, buffer1, 1, durMult, 2, amp, fv, fftVals[i], fftMax);
// 				};
//
// 				outFileLocal = PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++"_temp"++i;
// 				if((sf.duration*sf.numChannels*durMult*2)<(8*60*60))
// 				{
// 					headerFormat="wav";
// 					outFileLocal = PathName(outFileLocal).pathOnly++PathName(outFileLocal).fileNameWithoutExtension++".wav";
// 				}{
// 					headerFormat="caf";
// 					outFileLocal = PathName(outFileLocal).pathOnly++PathName(outFileLocal).fileNameWithoutExtension++".caf";
// 				};
//
// 				nrtJam.recordNRT(
// 					outputFilePath: outFileLocal.standardizePath,
// 					sampleRate: sf.sampleRate,
// 					headerFormat: headerFormat,
// 					sampleFormat: "int24",
// 					options: server.options,
// 					duration: sf.duration*durMult+3,
// 					action: action
// 				);
// 			};
//
// 		}{"Not an audio file!".postln;}
// 	}
//
// 	*addBundles {|nrtJam, server, inFile, buffer, chanNum, durMult, outChan, amp, fv, fftVal, fftMax|
//
// 		nrtJam.add([0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [chanNum])]);
//
// 		nrtJam.add([0.0, Synth.basicNew(("timeStretch3_"++fftVal.asInteger).asSymbol.postln, server).newMsg(args: [\out, outChan, \bufnum, buffer.bufnum, fftSize:fftVal.postln, fftMax:fftMax, \stretch, durMult, \hiPass, fv[0].postln, \lowPass, (fv[1]).postln, \amp, amp])])
// 		//nrtJam.add([0.0, Synth.basicNew((\pb_monoStretch2_Overlap2), server).newMsg(args: [\out, outChan, \bufnum, buffer.bufnum, fftSize:fftVals[i].postln, fftMax:fftMax, \stretch, durMult, \hiPass, fv[0].postln, \lowPass, (fv[1]).postln, \amp, amp])])
// 		//};
// 		^nrtJam
// 	}
//
//
//
// }
//
