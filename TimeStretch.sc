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

			SynthDef(\pb_monoStretch2, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, amp = 1, gate = 1|
				var trigPeriod, sig, chain, trig, pos, posB, stretchDur, jump, env, extraDel, bigEnv;
				//fftSize = 2**floor(log2(window*SampleRate.ir));
				trigPeriod = (fftSize/SampleRate.ir);
				trig = Impulse.ar(1/trigPeriod);

				stretchDur = BufDur.kr(bufnum)*stretch;
				jump = trigPeriod/(fftSize/2048)/stretchDur;

				pos = (LFSaw.ar(1/stretchDur, 1+(startPos*2), 0.5, 0.5));
				posB = (pos + jump);

				sig = GrainBuf.ar(1, trig, trigPeriod, bufnum, 1, [pos, posB], envbufnum: -1)*amp;
				sig = sig.collect({ |item, i|
					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: -1);
					chain = PV_Diffuser(chain, 1-trig);
					item = IFFT(chain, wintype: -1);
				});
				env = EnvGen.ar(Env.linen(trigPeriod/2, 0, trigPeriod/2, 1, 'wel'), trig)/***1.25*/;
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

	*stretchNRT { |inFile, outFile, durMult, fftSize = 8192, frameTuplet = 2, timeOffset=0|
		var server,buffer, envBuf, sf, argses, args, nrtJam, synthChoice, synths;

		"stretchy".postln;
		//inFile = inFile.fullPath.postln;
		if((inFile.extension=="wav")||(inFile.extension=="aif")){
			server = Server(("nrt"++NRT_Server_ID.next).asSymbol.postln,
				options: Server.local.options
				.numOutputBusChannels_(1)
				.numInputBusChannels_(1)
			);

			buffer = Buffer.new(server, 0, 1);

			sf = SoundFile.openRead(inFile.fullPath);
			sf.duration.postln;

			nrtJam = Score([
				[0.0, buffer.allocReadMsg(inFile.fullPath)],
				[0.0+timeOffset, Synth.basicNew("pb_monoStretch"++frameTuplet, server).newMsg(args: [bufnum: buffer.bufnum, fftSize:fftSize])],
			]);

			outFile.postln;

			nrtJam.recordNRT(
				outputFilePath: outFile.standardizePath,
				inputFilePath: buffer.path,
				sampleRate: 44100,
				headerFormat: "wav",
				sampleFormat: "int24",
				options: server.options,
				duration: sf.duration*durMult+timeOffset
			);

		}
	}

	*stretch { |target, bufferChan, outBus=0, pan=(-1), durMult=10, startPos = 0, fftSize = 8192, frameTuplet = 2, timeOffset=0|

		//if(synth!=nil){synth.set(\gate, 0)};
		synths.add(Synth.new("pb_monoStretch"++frameTuplet, [\out, outBus, \bufnum, bufferChan, \pan, pan, \stretch, durMult, \startPos, startPos, \fftSize, fftSize, \amp, 1, \gate, 1], target));
	}

	*stop {
		synths.do{|synth| if(synth!=nil){synth.set(\gate, 0)}};
		synths = List.newClear(0);
	}
}