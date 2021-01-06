NessWindow {
	*ar { |trig, rVal=0.25, widthSamples=8192|
		var fs = (1-(Slew.ar(
			1-Trig1.ar(trig, widthSamples/2/SampleRate.ir),
			SampleRate.ir/(widthSamples/2),
			SampleRate.ir/(widthSamples/2))));
		fs = (fs*pi/2).tan**2;
		^(fs*((1/(1+(2*fs*rVal)+(fs**2))).sqrt))
	}
}

TangentWindow {
	*ar { |trig, rVal=0.25, widthSamples=8192|
		var slew = Slew.ar(
			1-Trig1.ar(trig, widthSamples/2/SampleRate.ir),
			SampleRate.ir/(widthSamples/2),
			SampleRate.ir/(widthSamples/2));
		var fs = 1-slew;
		var phi = pi*fs/2;
		var denom = (1+(2*rVal*(phi.sin)*(phi.cos))).sqrt;
		// var sine = 1-(slew**2);

		^(phi.sin/denom)
	}
}

PaulWindow {
	*ar { |trig, rVal=0.25, widthSamples=8192|
		var slew = Slew.ar(
			1-Trig1.ar(trig, widthSamples/2/SampleRate.ir),
			SampleRate.ir/(widthSamples/2),
			SampleRate.ir/(widthSamples/2))**2;
		var fs = 1-slew;


		^(fs**(1+rVal))
	}
}

TestLSOF {
	*test{

	}
}

TimeStretch {
	classvar <>tanWindows, <>fftCosTables, startTime;

	*phaseRando {|array, lowBin, highBin|
		var real, imag, cosTable, phase, rando, complex, complex2, ifft, size, hann, mags, mags2, bins, idft, real2, imag2, spectrum, phases2;

		size = array.size;
		bins = (size/2).asInteger;

		hann = Signal.hanningWindow(size);

		real = Signal.newClear(size);
		real.waveFill({|i| array[i]}, 0, size-1);

		real = real*hann;

		imag = Signal.newClear(size);
		cosTable = Signal.fftCosTable(size);

		//complex = fft(real, cosTable);
		complex = fft(real, imag, cosTable);

		spectrum = FreqSpectrum.newComplex(complex);

		mags = complex.magnitude.deepCopy.asList;

		phases2 = complex.phase.deepCopy.asList;

		mags2 = Array.fill(mags.size, {0});
		(lowBin..highBin).do{|i| mags2.put(i, mags[i]); phases2.put(i, pi.rand)};

		spectrum.magnitude_(mags2.asArray);
		spectrum.phase_(phases2.asArray);

		ifft = spectrum.real.ifft(spectrum.imag, cosTable);
		^ifft.real
	}


	*phaseRandoRFFT {|array, lowBin, highBin|
		var real, imag, cosTable, phase, rando, complex, complex2, ifft, size, hann, mags, mags2, bins, idft, real2, imag2, spectrum, phases2;

		size = array.size;
		bins = (size/2).asInteger;

		hann = Signal.hanningWindow(size);

		real = Signal.newClear(size);
		real.waveFill({|i| array[i]}, 0, size-1);

		real = real*hann;

		cosTable = Signal.rfftCosTable(size/2+1);

		complex = real.rfft(cosTable);

		spectrum = FreqSpectrum.newComplex(complex);

		mags = complex.magnitude.deepCopy.asList;

		phases2 = complex.phase.deepCopy.asList;

		mags2 = Array.fill(mags.size, {0});
		(lowBin..highBin).do{|i| mags2.put(i, mags[i]); phases2.put(i, pi.rand)};

		spectrum.magnitude_(mags2.asArray);
		spectrum.phase_(phases2.asArray);

		ifft = spectrum.real.irfft(spectrum.imag, cosTable);
		^ifft.real
	}

	*makeWindows {
		var temp, windowSizeList, window;

		tanWindows = ();
		windowSizeList = List.newClear(0);
		(8..0).collect{|i|
			windowSizeList.add(2**(8+i));
		};
		windowSizeList.do{|windowSize|
			temp = List.newClear(0);
			(0, 0.01..0.3).do{|correlation|
				window = Signal.newClear(windowSize).waveFill({|fs|
					fs = (fs*pi/2).tan**2;
					fs*((1/(1+(2*fs*(correlation))+(fs**2))).sqrt)
				}, 0, 2);
				temp.add([window.copyRange(0, (windowSize/2-1).asInteger), window.copyRange((windowSize/2).asInteger, (windowSize-1).asInteger)]);
			};
			tanWindows.add(windowSize.asInteger.asSymbol -> temp);
		}
	}


	*makeFftCosTables {
		var windowSizeList;
		fftCosTables = ();
		windowSizeList = List.newClear(0);
		(8..0).collect{|i|
			windowSizeList.add(2**(8+i));
		};
		windowSizeList.do{|windowSize|
			fftCosTables.add(windowSize.asInteger.asSymbol -> Signal.fftCosTable(windowSize));
		}
	}

	*processChunk {|server, tempDir, floatArray, chanNum, outFolder, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType=0|
		var frameChunk = frameChunks[fCNum];
		var writeFile;
		var bigList = List.fill(chunkSize, {0});

		tanWindows.postln;
		fftCosTables.postln;

		("Chan Num: "++chanNum).postln;
		("Chunk "++(fCNum)++" of "++(frameChunks.size-1)).postln;

		9.do{|num|
			var correlations, getNext, windowSize, step, windowEnv, fftWind, numFrames, smallArrays, addArray, correlation, lowBin, highBin, pointer;
			var arrayA, arrayB;

			if(num==0){lowBin=32; highBin = 127}{lowBin=64; highBin = 127};
			lowBin.post;" ".post;highBin.postln;

			windowSize = (maxWindowSize/(2**num)).asInteger.postln;
			"num ".post;
			num.postln;
			"winSize ".post;
			windowSize.postln;

			numFrames = frameChunk/(windowSize/2);

			step = (windowSize/2)/durMult;
			"step ".postln; step.postln;

			if(fCNum==0){
				arrayA = Array.fill(windowSize, {0});
			}{
				arrayA = lastArrayA[num];
			};


			numFrames.do{|frameNum|
				//fCNum.postln; chunkSize.postln;
				pointer = (fCNum*(chunkSize/durMult))+(frameNum*step);
				//(pointer+windowSize-1).postln;
				if(fftType==0){
					arrayB = this.phaseRando(floatArray.copyRange((pointer).asInteger, (pointer+windowSize-1).asInteger), lowBin, highBin);
				}{
					arrayB = this.phaseRandoRFFT(floatArray.copyRange((pointer).asInteger, (pointer+windowSize-1).asInteger), lowBin, highBin);
				};

				smallArrays = [arrayA.copyRange((windowSize/2).asInteger, windowSize-1), arrayB.copyRange(0, (windowSize/2).asInteger-1)];

				if(smallArrays[0].sum!=0){
					correlation = (((smallArrays[0]*smallArrays[1]).sum)/((smallArrays[0]*smallArrays[0]).sum))}{correlation = 0};

				if(correlation<0){arrayB = arrayB.neg};

				fftWind = tanWindows[windowSize.asInteger.asSymbol][correlation.abs.linlin(0, 0.3, 0, 29).asInteger];
				addArray = ((smallArrays[0]*fftWind[1])+(arrayB.copyRange(0, (windowSize/2).asInteger-1)*fftWind[0]));
				addArray.do{|item, i|
					var index = (frameNum*(windowSize/2))+i;
					bigList.put(index, (item+bigList[index]))
				};
				arrayA = arrayB;
			};
			lastArrayA.put(num, arrayA);
		};

		writeFile = tempDir++PathName(outFolder).folderName++"_"++chanNum++"_"++fCNum++".wav";

		Buffer.loadCollection(server, bigList, 1, {|finalBuf0|
			"time: ".post;
			(Main.elapsedTime-startTime).postln;
			finalBuf0.write(writeFile);

			lastArrayA.writeArchive(tempDir++"lastArrays/"++PathName(outFolder).folderName++"_"++chanNum++"_"++fCNum++".lastArray");
			fCNum = fCNum+1;
			if(fCNum<frameChunks.size){
				"next Chunk".postln;

				this.processChunk(server, tempDir, floatArray, chanNum, outFolder, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType);
			}{
				"doneWChannel".postln;
				NetAddr("127.0.0.1", NetAddr.langPort).sendMsg(("/"++serverNum).asSymbol, "process next chan");
			}
		});

	}

	*stretch {|inFile, outFolder, dur, durMult=100, chanArray, chunkSize = 3276800, startFrame=0, fftType=0|
		var serverNum, server;

		serverNum = 57110+NRT_Server_Inc.next;
		while(
			{("lsof -i:"++serverNum++" ").unixCmdGetStdOut.size > 0},
			{serverNum = 57110+NRT_Server_Inc.next; serverNum.postln}
		);

		("server id: "++serverNum).postln;
		server = Server(("lang "++serverNum).asSymbol, NetAddr("127.0.0.1", serverNum),
			options: Server.local.options
		);

		server.waitForBoot{
			var maxWindowSize = 65536, numSamplesToProcess, sf;
			var totalFrames, totalChunks, frameChunks, tempDir;
			var lastArrayA, chanNum, chanCount;

			this.makeWindows;
			this.makeFftCosTables;

			startTime = Main.elapsedTime;

			if(outFolder.last.asString!="/"){outFolder=outFolder++"/"};

			tempDir = (PathName(outFolder).pathOnly).standardizePath;


			if(PathName(tempDir).isFolder.not){("mkdir "++tempDir.escapeChar($ )).systemCmd};
			if(PathName(tempDir++"lastArrays/").isFolder.not){("mkdir "++(tempDir++"lastArrays/").escapeChar($ )).postln.systemCmd};

			sf = SoundFile.openRead(inFile);
			if(dur<0){
				numSamplesToProcess=sf.numFrames;
				dur = sf.duration;
			}{numSamplesToProcess=sf.sampleRate*dur};
			numSamplesToProcess.postln;

			chanArray = chanArray ?? Array.fill(sf.numChannels, {|i| i});

			totalFrames = numSamplesToProcess*durMult;
			totalChunks = totalFrames/(chunkSize);

			frameChunks = Array.fill(totalChunks.floor, {chunkSize}).add(totalFrames-(totalChunks.floor*chunkSize));

			"Processing Chunks: ".post; totalChunks.postln;
			"Frame Chunks: ".post; frameChunks.postln;

			chanCount = 0;
			if(startFrame==0){
				lastArrayA = List.newClear(9);
			}{
				if(startFrame>0){
					var file;
					("loading last array from frame"++startFrame).post;
					file = (tempDir++"lastArrays/"++PathName(outFolder).folderName++"_"++chanArray[0]++"_"++(startFrame-1)++".lastArray").postln;
					file.postln;
					lastArrayA = Object.readArchive(file);
				}{
					var files, num, fileToLoad;
					"loading last array ".post;
					num = -1;
					files = PathName(tempDir).files;
					files.do{|file|
						var temp;
						temp = file.fileName.findAll("_");
						temp = file.fileName.copyRange(temp[temp.size-2]+1, temp.last-1).asInteger;
						temp.postln;
						if(temp==chanArray[0]){
							temp.postln;
							temp = file.fileName.findAll("_").addAll(file.fileName.findAll("."));
							temp = file.fileName.copyRange(temp[temp.size-2]+1, temp.last-1).asInteger;

							if(temp>num){num=temp};

					}};
					startFrame = num+1;
					if(startFrame>0){
						fileToLoad = (tempDir++"lastArrays/"++PathName(outFolder).fileNameWithoutExtension++"_"++chanArray[0]++"_"++(startFrame-1)++".lastArray").postln;
						lastArrayA = Object.readArchive(fileToLoad);
					}{
						lastArrayA = List.newClear(9);
					}
				}
			};


			chanNum = chanArray[0];

			Buffer.readChannel(server, inFile, 0, -1, [chanNum], {|buffer|
				buffer.loadToFloatArray(action:{|floatArray|
					floatArray = floatArray.addAll(FloatArray.fill(maxWindowSize*2, {0}));
					this.processChunk(server, tempDir, floatArray, chanNum, outFolder, maxWindowSize, durMult, chunkSize, frameChunks, startFrame, lastArrayA, serverNum, fftType);
				});
			});

			OSCFunc({|msg, time, addr, recvPort|
				msg.postln;
				"nextChan".postln;
				chanCount = chanCount+1;
				chanCount.postln;
				if(chanArray[chanCount]!=nil){
					chanNum = chanArray[chanCount];
					Buffer.readChannel(server, inFile, 0, -1, [chanCount], {|buffer|
						buffer.loadToFloatArray(action:{|floatArray|
							floatArray = floatArray.addAll(FloatArray.fill(maxWindowSize, {0}));
							this.processChunk(server, tempDir, floatArray, chanNum, outFolder, maxWindowSize, durMult, chunkSize, frameChunks, 0, lastArrayA, serverNum, fftType);
						});
					});
				}{
					"we're done".postln;
					server.quit;
				}
			}, ("/"++serverNum).asSymbol);
		}
	}

	*mergeFiles {|server, folder, numChans=2|
		server.waitForBoot{
			var files, channels, chunkSize;


			var buffers, counter, doit;

			var finalBuf = Buffer(server);



			if(folder.last.asString!="/"){folder = folder++"/"};
			folder.postln;
			folder = PathName(folder);

			files = folder.files.select{arg file; file.extension=="wav"};
			channels = numChans.collect{|chan| files.select{arg file; file.fullPath.contains("_"++chan++"_")}};

			chunkSize = SoundFile.openRead(files[0].fullPath).numFrames;

/*			var doit1 = {|counter|
				"chan1".postln;
				if(counter<buffers[1].size){
					FluidBufCompose.process(server, buffers[1][counter], 0, -1, 0, -1, 1, finalBuf, counter*chunkSize, 1, action:{doit1.value(counter+1)});
				}{
					"write file".postln;
					finalBuf.duration.postln;
					if((finalBuf.duration*finalBuf.numChannels)>5000){
						finalBuf.write(folder.fullPath++folder.folderName++".w64", "w64", "int24");
					}{
						finalBuf.write(folder.fullPath++folder.folderName++".wav", "wav", "int24");
					}
				}
			};

			var doit0 = {|counter|
				("chan0"+"file"+counter).postln;
				if(counter<buffers[0].size){
					FluidBufCompose.process(server, buffers[0][counter], 0, -1, 0, -1, 1, finalBuf, counter*chunkSize, 0, action:{doit0.value(counter+1)})
				}{
					if(numChans>1){
						FluidBufCompose.process(server, buffers[1][0], 0, -1, 0, -1, 1, finalBuf, 0, 0, 0,  action:{doit1.value(0)});
					}{
						"write file".postln;
						if((finalBuf.duration*finalBuf.numChannels)>5000){
							"w64".postln;
							finalBuf.write(folder.fullPath++folder.folderName++".w64", "w64", "int24");
						}{
							finalBuf.write(folder.fullPath++folder.folderName++".wav", "wav", "int24");
						}
					}
				}
			};*/
			"merge files!".postln;
			//FluidBufCompose.process(server, buffers[0][0], 0, -1, 0, -1, 1, finalBuf, 0, 0, 0,  action:{doit0.value(0, 0)});

			doit = {Routine{
				server.sync;
				buffers.do{|chan, i|
					chan.do{|buf, i2|
						("chan:"+i+"buffer"+i2).postln;
						FluidBufCompose.process(server, buf, 0, -1, 0, -1, 1, finalBuf, i*chunkSize, i2, 0);

					};
				};
				server.sync;
				"write file".postln;
				//server.sync;
				finalBuf.query;
				server.sync;
				finalBuf.duration.postln; finalBuf.numChannels.postln;
				if((finalBuf.duration*finalBuf.numChannels)>5000){
					"w64".postln;
					finalBuf.write(folder.parentPath++folder.folderName++".w64", "w64", "int24");
				}{
					finalBuf.write(folder.parentPath++folder.folderName++".wav", "wav", "int24");
				}
			}.play};
			counter = 0;
			buffers = channels.collect{|chan| chan.collect{|file| Buffer.read(server, file.fullPath, 0, -1, {
				counter = counter+1;
				counter.postln;
				if(counter==files.size){"buffers loaded".postln; doit.value};
			})
			}
			};
		};
	}

}

// TimeStretch {
// 	classvar synths;
// 	//by Sam Pluta - sampluta.com
// 	// Based on the Alex Ness's NessStretch algorithm in Python
// 	// thanks to Jean-Philippe Drecourt for his implementation of Paul Stretch, which was a huge influence on this code
//
// 	*initClass {
// 		synths = List.newClear(0);
// 		StartUp.add {
//
// 			SynthDef(\pb_monoStretch_Overlap4, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, amp = 1, gate = 1|
// 				var trigPeriod, sig, chain, trig, pos, jump, trigEnv, bigEnv;
//
// 				trigPeriod = (fftSize/SampleRate.ir);
// 				trig = Impulse.ar(1/trigPeriod);
//
// 				jump = fftSize/stretch/4;
//
// 				startPos = (startPos%1);
// 				pos = Line.ar(startPos*BufFrames.kr(bufnum), BufFrames.kr(bufnum), BufDur.kr(bufnum)*stretch*(1-startPos));
//
// 				pos = [pos, pos + jump, pos + (2*jump), pos + (3*jump)];
//
// 				sig = PlayBuf.ar(1, bufnum, 1, trig, pos, 1)*SinOsc.ar(1/(2*trigPeriod)).abs;
//
// 				sig = sig.collect({ |item, i|
// 					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: 0);
// 					chain = PV_Diffuser(chain, 1-trig);
// 					chain = PV_BrickWall(chain, hiPass);
// 					chain = PV_BrickWall(chain, lowPass);
// 					item = IFFT(chain, 0);
// 				});
//
// 				sig = DelayC.ar(sig*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);
//
// 				sig[1] = DelayC.ar(sig[1], trigPeriod/4, trigPeriod/4);
// 				sig[2] = DelayC.ar(sig[2], trigPeriod/2, trigPeriod/2);
// 				sig[3] = DelayC.ar(sig[3], 3*trigPeriod/4, 3*trigPeriod/4);
//
// 				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
// 				Out.ar(out, Pan2.ar(Mix.new(sig), pan)*0.5*bigEnv);
// 			}).writeDefFile;
//
// 			SynthDef(\pb_monoStretch_Overlap2, { |out = 0, bufnum, pan = 0, stretch = 12, startPos = 0, fftSize = 8192, fftMax = 65536, hiPass = 0, lowPass=0, wintype = 1, amp = 1, gate = 1, winExp = 1.2|
// 				var trigPeriod, sig, chain, trig, pos, jump, trigEnv, fftDelay, paulEnv, winChoice, bigEnv, warp;
//
// 				trigPeriod = (fftSize/SampleRate.ir);
// 				trig = Impulse.ar(1/trigPeriod);
//
// 				startPos = (startPos%1);
// 				pos = Line.ar(startPos*BufFrames.kr(bufnum), BufFrames.kr(bufnum), BufDur.kr(bufnum)*stretch*(1-startPos));
//
// 				jump = fftSize/stretch/2;
// 				pos = [pos, pos + jump];
//
// 				paulEnv = (1-(Slew.ar(
// 					1-Trig1.ar(trig, fftSize/2/SampleRate.ir),
// 					SampleRate.ir/(fftSize/2),
// 				SampleRate.ir/(fftSize/2))**2))**1.25;
//
// 				sig = PlayBuf.ar(1, bufnum, 1, trig, pos, 1)*SinOsc.ar(1/(2*trigPeriod)).abs;
//
// 				winChoice = Select.kr(wintype, [0, -1]);
//
// 				sig = sig.collect({ |item, i|
// 					chain = FFT(LocalBuf(fftSize), item, hop: 1.0, wintype: 0);
// 					chain = PV_Diffuser(chain, 1-trig);
// 					chain = PV_BrickWall(chain, hiPass);
// 					chain = PV_BrickWall(chain, lowPass);
// 					item = IFFT(chain, wintype: winChoice);
// 				});
//
// 				warp = (SinOsc.ar(1/(2*trigPeriod))**(winExp-1)).abs.clip(0.001, 1);
//
// 				trigEnv = Select.ar(wintype,
// 				[warp, paulEnv]);
//
// 				fftDelay = fftSize-BlockSize.ir/SampleRate.ir;
// 				trigEnv = DelayC.ar(trigEnv, fftDelay, fftDelay);
// 				sig = sig*trigEnv;
//
// 				sig = DelayC.ar(sig*amp, fftMax-fftSize/SampleRate.ir, fftMax-fftSize/SampleRate.ir);
//
// 				sig[1] = DelayC.ar(sig[1], trigPeriod/2, trigPeriod/2);
//
// 				bigEnv = EnvGen.kr(Env.asr(0,1,0), gate, doneAction:2);
// 				Out.ar(out, Pan2.ar(Mix.new(sig), pan)/2*bigEnv);
// 			}).writeDefFile;
//
// 		}
// 	}
//
// 	*stretch { |inFile, outFile, durMult, fftMax = 65536, overlaps = 2, numSplits = 9, wintype = 0, winExp=1.1, amp = 1, action|
// 		var sf, argses, args, nrtJam, synthChoice, synths, numChans, server, buffer0, buffer1, filtVals, fftVals, fftBufs, headerFormat;
//
// 		action ?? {action = {"done stretchin!".postln}};
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
// 			if(outFile == nil){outFile = inFile.pathOnly++inFile.fileNameWithoutExtension++durMult++".wav"};
//
// 			//Server.local.options.verbosity_(verbosity);
//
// 			server = Server(("nrt"++NRT_Server_ID.next).asSymbol,
// 				options: Server.local.options
// 				.numOutputBusChannels_(numChans)
// 				.numInputBusChannels_(numChans)
// 			);
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
// 			buffer0 = Buffer.new(server, 0, 1);
// 			buffer1 = Buffer.new(server, 0, 1);
//
// 			nrtJam = Score.new();
//
// 			nrtJam = this.addBundles(nrtJam, server, inFile, buffer0, 0, durMult, overlaps, -1, amp, filtVals, fftVals, fftMax, wintype, winExp);
// 			if(numChans>1){
// 				nrtJam = this.addBundles(nrtJam, server, inFile, buffer1, 1, durMult, overlaps, 1, amp, filtVals, fftVals, fftMax, wintype, winExp);
// 			};
//
// 			if((sf.duration*sf.numChannels*durMult)<(8*60*60)){headerFormat="wav"}{
// 				headerFormat="caf";
// 				outFile = PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++".caf";
// 			};
//
//
// 			nrtJam.recordNRT(
// 				outputFilePath: outFile.standardizePath,
// 				sampleRate: sf.sampleRate,
// 				headerFormat: headerFormat,
// 				sampleFormat: "int24",
// 				options: server.options,
// 				duration: sf.duration*durMult+3,
// 				action: action
// 			);
//
// 		}{"Not an audio file!".postln;}
// 	}
//
// 	*addBundles {|nrtJam, server, inFile, buffer, chanNum, durMult, overlaps, pan, amp, filtVals, fftVals, fftMax, wintype, winExp|
//
// 		nrtJam.add([0.0, buffer.allocReadChannelMsg(inFile.fullPath, 0, -1, [chanNum])]);
// 		filtVals.do{|fv, i|
// 			switch(overlaps[i],
// 				2, {overlaps.put(i, 2)},
// 				{overlaps.put(i, 4)}
// 			);
//
// 			nrtJam.add([0.0, Synth.basicNew(("pb_monoStretch_Overlap"++overlaps[i]), server).newMsg(args: [bufnum: buffer.bufnum, pan: pan, fftSize:fftVals[i].postln, fftMax:fftMax, \stretch, durMult, \hiPass, fv[0], \lowPass, fv[1]-1, \wintype, wintype[i],\amp, amp, \winExp, winExp[i].postln])])
// 		};
// 		^nrtJam
// 	}
//
// }