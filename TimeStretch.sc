NRT_Server_ID {
	classvar <id=5000;
	*initClass { id = 5000; }
	*next  { ^id = id + 1; }
	*path {this.filenameSymbol.postln}
}

TimeStretch {
	classvar synths;
	//by Sam Pluta - sampluta.com
	// Based on the Jean-Philippe Drecourt's port of Paul's Extreme Sound Stretch algorithm by Nasca Octavian PAUL
	// https://github.com/paulnasca/paulstretch_python/blob/master/paulstretch_steps.png
	//http://drecourt.com

	*initClass {
		synths = List.newClear(0);
		StartUp.add {

			SynthDef(\pb_monoStretch2_Overlap4, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, hiPass = 0, lowPass=0, amp = 1, gate = 1|
			var trigPeriod, sig, chain, trig, pos, posB, stretchDur, jump, env, extraDel, bigEnv, count, totFrames, fftBufs, trigEnv;
			trigPeriod = (fftSize/SampleRate.ir);
			trigEnv = EnvGen.ar(Env([0,0,1], [1,0]), 1);
			trig = Impulse.ar(1/trigPeriod);

			totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);

			count = (PulseCount.ar(trig)-1+(totFrames*startPos));

		//count.poll;

			jump = trigPeriod/BufDur.kr(bufnum)/stretch/4;
			pos = Line.ar(0, 1, BufDur.kr(bufnum)*stretch);

		pos = [pos, pos + jump, pos + (2*jump), pos + (3*jump)];
		//Latch.ar(pos, trig).poll(20);

			sig = GrainBuf.ar(1, trig, trigPeriod, bufnum, 1, pos, envbufnum: -1);
			sig = sig.collect({ |item, i|
				chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
				chain = PV_Diffuser(chain, 1-trig);
				chain = PV_BrickWall(chain, hiPass);
				chain = PV_BrickWall(chain, lowPass);
				item = IFFT(chain, wintype: -1);
			});

			env = EnvGen.ar(Env.linen(5*trigPeriod/13, 0, 5*trigPeriod/13, 1, 'wel'), trig);
			bigEnv = EnvGen.kr(Env.asr(0,1,0), gate);
			sig = sig*env*bigEnv*amp;
			sig[1] = DelayC.ar(sig[1], trigPeriod/4, trigPeriod/4);
			sig[2] = DelayC.ar(sig[2], trigPeriod/2, trigPeriod/2);
			sig[3] = DelayC.ar(sig[3], 3*trigPeriod/4, 3*trigPeriod/4);
			Out.ar(out, Pan2.ar(Mix.new(sig), pan)/2);
		}).writeDefFile;


			SynthDef(\pb_monoStretch2_Overlap2, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, amp = 1, gate = 1|
				var trigPeriod, sig, chain, trig, pos, posB, stretchDur, jump, env, extraDel, bigEnv, count, totFrames;

				trigPeriod = (fftSize/SampleRate.ir);
				trig = Impulse.ar(1/trigPeriod);

				totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);
				count = (PulseCount.ar(trig)-1+(totFrames*startPos));

				jump = 1/totFrames/2;

				pos = count/totFrames;
				pos = [pos, pos + jump];

				sig = GrainBuf.ar(1, trig, trigPeriod, bufnum, 1, pos, envbufnum: -1)*amp;
				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
					chain = PV_Diffuser(chain, 1-trig);
					item = IFFT(chain, wintype: -1);
				});
				env = EnvGen.ar(Env.linen(trigPeriod/2, 0, trigPeriod/2, 1, 'wel'), trig);
				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate);
				sig = sig*env*bigEnv;
				sig[1] = DelayC.ar(sig[1], trigPeriod/2, trigPeriod/2);
				Out.ar(out, Pan2.ar(Mix.new(sig), pan));
			}).writeDefFile;

			SynthDef(\pb_monoStretch3, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, timeDisp = 0, amp = 1, gate = 1|
				// Paulstretch for SuperCollider

				var trigPeriod, sig, chain, trig, pos, posB, fftDur, stretchDur, jump, env, bigEnv;

				fftDur = fftSize/SampleRate.ir;

				trigPeriod = (fftSize/SampleRate.ir);
				trig = Impulse.ar(1/trigPeriod);

				stretchDur = BufDur.kr(bufnum)*stretch;
				jump = trigPeriod/(fftSize/2048)/stretchDur*(2/3);

				pos = (LFSaw.ar(1/stretchDur, 1+(startPos*2), 0.5, 0.5));
				pos = [pos, pos+jump, pos+(jump*2)];

				sig = GrainBuf.ar(1, trig, 2*trigPeriod/3, bufnum, 1, pos, envbufnum: -1)*amp;
				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
					chain = PV_Diffuser(chain, 1-trig);
					item = IFFT(chain, wintype: -1);
				});
				env = EnvGen.ar(Env.linen(trigPeriod/2, 0, trigPeriod/2, 1, 'wel'), trig);
				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate);
				sig = sig*env*bigEnv;
				sig[1] = DelayC.ar(sig[1], 2*trigPeriod/3, trigPeriod/3);
				sig[2] = DelayC.ar(sig[2], trigPeriod, 2*trigPeriod/3);

				Out.ar(out, Pan2.ar(Mix.new(sig), pan));
			}).writeDefFile;

			SynthDef(\pb_monoStretch5, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, amp = 1, gate = 1|

				var trigPeriod, sig, chain, trig, pos, posB, fftDur, stretchDur, jump, env, trigDiv, bigEnv;

				fftDur = fftSize/SampleRate.ir;

				trigPeriod = fftDur;
				trig = Impulse.ar(1/trigPeriod);

				stretchDur = BufDur.kr(bufnum)*stretch;
				jump = trigPeriod/(fftSize/2048)/stretchDur*(1/5);

				pos = (LFSaw.ar(1/stretchDur, 1+(startPos*2), 0.5, 0.5));
				pos = [pos, pos+jump, pos+(jump*2), pos+(jump*3), pos+(jump*4)];

				sig = GrainBuf.ar(1, trig, 4*fftDur/5, bufnum, 1, pos, envbufnum: -1)*amp;
				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
					chain = PV_Diffuser(chain, 1-trig);
					item = IFFT(chain, wintype: -1);
				});
				trigDiv = PulseDivider.ar(trig, 2);
				env = EnvGen.ar(Env.linen(trigPeriod/2, 0, trigPeriod/2, 1, 'wel'), trig);
				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate);
				sig = sig*env*bigEnv;
				sig[1] = DelayC.ar(sig[1], 2*trigPeriod/5, 2*trigPeriod/5);
				sig[2] = DelayC.ar(sig[2], 4*trigPeriod/5, 4*trigPeriod/5);
				sig[3] = DelayC.ar(sig[3], 6*trigPeriod/5, 6*trigPeriod/5);
				sig[4] = DelayC.ar(sig[4], 8*trigPeriod/5, 8*trigPeriod/5);

				Out.ar(out, Pan2.ar(Mix.new(sig), pan));
			}).writeDefFile;


		}
	}

	*stretchNRT { |inFile, outFileIn, durMult, fftMax = 65536, frameTuplet = 2, timeOffset=0, overlaps=4, fftBreaks=6|
		var sf, argses, args, nrtJam, synthChoice, synths, numChans;

		"stretchy".postln;
		inFile = PathName(inFile);
		if((inFile.extension=="wav")||(inFile.extension=="aif")){

			sf = SoundFile.openRead(inFile.fullPath);
			sf.duration.postln;

			numChans = sf.numChannels;

			numChans.do{|i|
				var server,buffer, filtVals, fftVals, fftBufs, outFile;

				outFile = outFileIn;
				if(numChans>1){outFile = PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++i++".wav"};

				server = Server(("nrt"++NRT_Server_ID.next).asSymbol.postln,
					options: Server.local.options
					.numOutputBusChannels_(1)
					.numInputBusChannels_(1)
				);

				filtVals = List.fill(8, {|i| 1/2**(i+1)}).dup.flatten.add(0).add(1).sort.clump(2);
				//filtVals = List[ 0.25, 0.125, 0.0625, 0.03125, 0.015625, 0.0078125 ].dup.flatten.add(0).add(1).sort.clump(2);

				filtVals.postln;
				fftVals = List.fill(filtVals.size.postln, {|i| fftMax/(2**i)});

				fftVals.postln;

				buffer = Buffer.new(server, 0, 1);

				nrtJam = Score([
					[0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [i])],

					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[0], \stretch, durMult, \hiPass, filtVals[0][0], \lowPass, filtVals[0][1]-1, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[1], \stretch, durMult, \hiPass, filtVals[1][0], \lowPass, filtVals[1][1]-1, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[2], \stretch, durMult, \hiPass, filtVals[2][0], \lowPass, filtVals[2][1]-1, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[3], \stretch, durMult, \hiPass, filtVals[3][0], \lowPass, filtVals[3][1]-1, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[4], \stretch, durMult, \hiPass, filtVals[4][0], \lowPass, filtVals[4][1]-1, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[5], \stretch, durMult, \hiPass, filtVals[5][0], \lowPass, filtVals[5][1]-1, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[6].postln, \stretch, durMult, \hiPass, filtVals[6][0].postln, \lowPass, (filtVals[6][1]-1).postln, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[7], \stretch, durMult, \hiPass, filtVals[7][0], \lowPass, filtVals[7][1]-1, \amp, 1])],
					[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftVals[8], \stretch, durMult, \hiPass, filtVals[8][0], \lowPass, filtVals[8][1]-1, \amp, 1])]
				]);

				outFile.postln;

				nrtJam.recordNRT(
					outputFilePath: outFile.standardizePath,
					inputFilePath: buffer.path,
					sampleRate: 44100,
					headerFormat: "wav",
					sampleFormat: "int24",
					options: server.options,
					duration: sf.duration*durMult+timeOffset+5
				);

			}
		}
	}

	*stretch { |target, bufferChan, outBus=0, pan=(-1), durMult=10, startPos = 0, fftSize = 8192, frameTuplet = 2, timeOffset=0, overlaps=2|

		if(overlaps!=4){overlaps=2};
		synths.add(Synth.new("pb_monoStretch"++frameTuplet++"_Overlap"++overlaps, [\out, outBus, \bufnum, bufferChan, \pan, pan, \stretch, durMult, \startPos, startPos, \fftSize, fftSize, \amp, 1, \gate, 1], target));
	}

	*stop {
		synths.do{|synth| if(synth!=nil){synth.set(\gate, 0)}};
		synths = List.newClear(0);
	}
}