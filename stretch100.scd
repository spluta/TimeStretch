t = Server("serv2", NetAddr("127.0.0.1", 57111), s.options)
t.serverOptions
t.boot
b = Buffer.readChannel(s, "/Volumes/StretchFiles/Charli/charli_blame.wav", channels:[0]);


t = TimeStretch
t.stretchNRT("/Volumes/StretchFiles/Charli/charli_blame1Sec.wav", "/Volumes/StretchFiles/Charli/charli_blame1SecLong.wav", 100)


	c = Buffer.read(t, "/Volumes/StretchFiles/Charli/Main/Chans/Chan0/charli_blame_Chan0_0000.wav");

	(
		SynthDef(\pb_monoStretch2_Overlap4, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, hiPass = 0, lowPass=0, amp = 1, gate = 1|
			var trigPeriod, sig, chain, trig, pos, posB, stretchDur, jump, env, extraDel, bigEnv, count, totFrames, fftBufs, trigEnv;
			trigPeriod = (fftSize/SampleRate.ir);
			trigEnv = EnvGen.ar(Env([0,0,1], [1,0]), 1);
			trig = Impulse.ar(1/trigPeriod);

			totFrames = (BufFrames.kr(bufnum)/fftSize*stretch);

			count = (PulseCount.ar(trig)-1+(totFrames*startPos));

		//count.poll;

			//jump = 1/totFrames/64;
			jump = trigPeriod/BufDur.kr(bufnum)/stretch/4;
			pos = Line.ar(0, 1, BufDur.kr(bufnum)*stretch);

			//pos.poll(0.1);
		pos = [pos, pos + jump, pos + (2*jump), pos + (3*jump)];
		//pLatch.ar(pos, trig).poll(20);

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
		}).load(s)
	)


0.0241247-0.0160832


0.0179038-0.0160832

0.0160832-0.0120624/4

0.0160832-0.00804165

0.0259741-0.025433
(65536/44100)/b.duration/100/4

0.0001352825*(44100/65536)

0.00974036-0.00919923

b.numFrames/65536*100/0.00054112

b.duration

0.0101173-0.00957618
0.00919923-0.00974036

	g = Array.fill(4, {Buffer.alloc(s, 65536)})

	Synth(\pb_monoStretch2_Overlap4, [\out,0, \bufnum,b, \pan,0, \stretch, 100, \startPos, 0, \fftSize, (65536/256).postln, \hiPass, 0.5, \lowPass, 0, \amp, 0.5], s);

	b.play
	a = List[ 0.25, 0.125, 0.0625, 0.03125, 0.015625, 0.0078125 ].add(0).add(1)

	a = a.reverse
	a.size

	b = Buffer.readChannel(s, "/Volumes/StretchFiles/Charli/charli_blame.wav", channels:[0]);
	(
		a = List[ 0.25, 0.125, 0.0625, 0.03125, 0.015625, 0.0078125 ].dup.flatten.add(0).add(1).sort.clump(2);

		d = List.newClear(0);
		a.do{|item, i|
			item.postln;
			SystemClock.schedAbs(0,{
				d.add(Synth(\pb_monoStretch2_Overlap2, [\out,0, \bufnum,b, \pan,-1, \stretch, 100, \startPos, 0, \fftSize, (65536/(2**i)).postln, \hiPass, item[0], \lowPass, item[1]-1, \amp, 0.5], s));
				//e.add(Synth(\pb_monoStretch2_Overlap4, [\out,0, \bufnum,c, \pan,1, \stretch, 100, \startPos, 0, \fftSize, (65536/(2**i)).postln, \hiPass, item[0], \lowPass, item[1]-1, \amp, 0.5], t));
		})}
	)


	d.do{|item| item.set(\amp,0)};
	e.do{|item| item.set(\amp,0)};

	d[0].set(\amp, 1)
	b[5].set(\amp, 0)


	a = Synth(\pb_monoStretch2_Overlap4, [\out,0, \bufnum,b, \pan,0, \stretch, 100, \startPos, 0, \fftSize, 512, \hiPass, 0.5, \lowPass, 0]);
	a.free

	a.set(\hiPass, 1)

	TimeStretch.stop


	//NMF stretch version


	f = FluidNMFStretch(s, "/Volumes/StretchFiles/Charli/charli_blame.wav")

	f.makeFolders  //make the enclosing folder

	f.nmf(50);  //calculate and save the NMF channels

	f.stretch(100, "NewStretch6", 65536, 0)

	f.getCentroid  //get the centroid for the variStretch
	f.variWindowStretch(100, "Stretch100", 0.05, 2)  //this may melt your computer


	0.142737-0.142234/4

	0.142737-0.142611-0.142486-0.14236-0.142737

	0.000502595/4

	0.0105545-0.0100519/4

	0.0105545-0.0106801-0.0108058-0.0109314

	UGen Array [3]:
	UGen(BinaryOpUGen): 0.000125649
	UGen Array [0]:
	UGen Array [1]: 0.0106801
	UGen Array [2]: 0.0108058
	UGen Array [3]: 0.0109314



	UGen Array [1]: 0.14236
	UGen Array [2]: 0.142486
	UGen Array [3]: 0.142611
	UGen Array [0]: 0.142737

	1024/SampleRate.ir/stetchDur;

	8192/44100/(b.duration*100)

	0.000760872-0.000509574/2

	0.323671-0.323546-0.32342-0.323294


	0.0373377-0.0371735

	0.0371735-0.0370479

	0.0497836-0.0496194

	0.0496194-0.0494937

	0.0494937-0.0493681

	0.0493681-0.0492424

	0.0497836-0.0492424/4

	8912/44100

	1024/SampleRate.ir/stetchDur;

	1024/44100/(b.duration*100)