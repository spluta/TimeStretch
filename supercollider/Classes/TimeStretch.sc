//Copyright © 2021 Sam Pluta - sampluta.com
//Free Software! - Released under GPLv3 License

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

NRT_Server_ID {
	classvar <id=5000;
	*initClass { id = 5000; }
	*next  { ^id = id + 1; }
	*path {this.filenameSymbol.postln}
}

NRT_Server_Inc {
	classvar <id=0;
	*initClass { id = 0; }
	*next  { ^id = id + 1; }
	*path {this.filenameSymbol.postln}
}

TimeStretch {
	classvar <>tanWindows, <>linkwitzRileyWindows, <>fftCosTables, <>hannWindows, startTime;
	classvar serverNum, server;


	*getPolar {|complex, lowBin, highBin, linkwitzRileyWindow, filterOrder|
		var mags, mags2, phases2;

		if(filterOrder<129){
			mags2 = complex.magnitude*linkwitzRileyWindow;
			phases2 = complex.phase.collect{|phase, i| if(mags2[i]>0){pi.rand}{phase}};
		}{
			mags = complex.magnitude.asList;
			mags2 = Array.fill(mags.size, {0});
			phases2 = complex.phase.deepCopy.asList;
			(lowBin..highBin).do{|i| mags2.put(i, mags[i]); phases2.put(i, pi.rand)};
		};
		^Polar(mags2, phases2.asArray);
	}

	*phaseRandoFFT{|arrayA, lowBin, highBin, linkwitzRileyWindow, filterOrder|
		var real1, real2, imag, fftSize, cosTable, hann, polar1, polar2, complex, complex2, ifft;

		fftSize = arrayA.size;
		cosTable = Signal.fftCosTable(fftSize);
		hann = hannWindows[fftSize.asSymbol];

		real1 = arrayA.as(Signal)*hann;
		imag = Signal.newClear(fftSize);
		complex = fft(real1, imag, cosTable);

		polar1 = this.getPolar(complex, lowBin, highBin, linkwitzRileyWindow, filterOrder);
		complex2 = polar1.asComplex;

		ifft = ifft(complex2.real.as(Signal), complex2.imag.as(Signal), cosTable);

		^ifft.real*2
	}

	*phaseRandoDualRFFT{|arrayA, arrayB, lowBin, highBin, linkwitzRileyWindow, filterOrder|
		var real1, real2, imag, rfftSize, cosTable, hann, polar1, polar2, complexDict, irfftDict;

		rfftSize = arrayA.size/2+1;
		cosTable = Signal.rfftTwoCosTable(rfftSize);
		hann = hannWindows[arrayA.size.asSymbol];

		real1 = arrayA.as(Signal)*hann;
		real2 = arrayB.as(Signal)*hann;

		complexDict = rfftTwo(real1, real2, cosTable);
		polar1 = this.getPolar(complexDict[\rfft1], lowBin, highBin, linkwitzRileyWindow, filterOrder);
		polar2 = this.getPolar(complexDict[\rfft2], lowBin, highBin, linkwitzRileyWindow, filterOrder);

		irfftDict = polar1.real.as(Signal).irfftTwo(polar1.imag.as(Signal), polar2.real.as(Signal), polar2.imag.as(Signal), cosTable);

		^[irfftDict[\irfft1], irfftDict[\irfft2]]
	}

	*makeWindows {|winType=0|
		var temp, windowSizeList, window;

		tanWindows = ();
		windowSizeList = List.newClear(0);
		(8..0).collect{|i|
			windowSizeList.add(2**(8+i));
		};

		switch(winType,
			0, {
				"Ness Window".postln;
			},
			1, {
				"Tan Window".postln;
			},
			2, {
				"Sine Window".postln;
		});

		windowSizeList.do{|windowSize|
			temp = List.newClear(0);
			(0, 0.01..1.0).do{|correlation|
				switch(winType,
					0, {
						/*window = Signal.newClear(windowSize).waveFill({|fs|
							fs = (fs*pi/2).tan**2;
							fs*((1/(1+(2*fs*(correlation))+(fs**2))).sqrt)
						}, 0, 2);*/
						window = Signal.newClear(windowSize-1).waveFill({|fs|
							fs = (fs*pi/2).tan**2;
							fs*((1/(1+(2*fs*(1))+(fs**2))).sqrt)
						}, 0, 2).add(0.0)

					},
					1, {
						window = Signal.newClear(windowSize/2).waveFill({|fs|
							var phi, denom;
							phi = pi*fs/2;
							denom = (1+(2*correlation*(phi.sin)*(phi.cos))).sqrt;
							phi.sin/denom
						}, 0, 1).addAll(
							Signal.newClear(windowSize/2).waveFill({|fs|
								var phi, denom;
								phi = pi*fs/2;
								denom = (1+(2*correlation*(phi.sin)*(phi.cos))).sqrt;
								phi.sin/denom
							}, 0, 1).reverse
						);

					},
					2, {
						window = Signal.sineFill(windowSize*2, [1]).copyRange(0, windowSize.asInteger);
						window = ((1-correlation)*window)+(correlation*(window*window));
					}
				);
				temp.add([window.copyRange(0, (windowSize/2-1).asInteger), window.copyRange((windowSize/2).asInteger, (windowSize-1).asInteger)]);
			};
			tanWindows.add(windowSize.asInteger.asSymbol -> temp);
		}
	}

	*makeFftCosTables {
		var windowSizeList;
		fftCosTables = ();
		hannWindows = ();
		windowSizeList = List.newClear(0);
		(8..0).collect{|i|
			windowSizeList.add(2**(8+i));
		};
		windowSizeList.do{|windowSize|
			fftCosTables.add(windowSize.asInteger.asSymbol -> Signal.fftCosTable(windowSize));
			hannWindows.add(windowSize.asInteger.asSymbol -> Signal.hanningWindow(windowSize));
		}
	}

	*processChunk {|server, sfFinal, floatArray, chanNum, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType=0, binShift = 0, filterOrder=129|
		var frameChunk = frameChunks[fCNum];
		var writeFile;
		var bigList = List.fill(chunkSize, {0});
		var binShiftSamples, linkwitzRileyWindow;

		("Chan Num: "++chanNum).postln;
		("Chunk "++(fCNum)++" of "++(frameChunks.size-1)).postln;

		windowSizes.size.do{|num|
			var correlations, getNext, windowSize, step, windowEnv, fftWind, numFrames, smallArrays, addArray, correlation, lowBin, highBin, pointer, tempArray, frames;
			var arrayA, arrayB;

			if(num==0){lowBin=0; highBin = 127}{lowBin=64; highBin = 127};

			windowSize = windowSizes[num];
			if(fftType == 0){
				linkwitzRileyWindow = Signal.linkwitzRileyBP(windowSize/2, lowBin-1, highBin, filterOrder).addAll(Signal.fill(windowSize/2, {0}));
			}{
				linkwitzRileyWindow = Signal.linkwitzRileyBP(windowSize/2+1, lowBin-1, highBin, filterOrder);
			};
/*			"num ".post;
			num.postln;
			"winSize ".post;
			windowSize.postln;*/

			step = (windowSize/2)/durMult;
			//"step: ".post; step.postln;

			numFrames = (frameChunk/step/durMult).asInteger;
			//"numFrames: ".post; numFrames.postln;*/

			if(fCNum==0){
				arrayA = Array.fill(windowSize, {0});
			}{
				arrayA = lastArrayA[num];
			};

			if(binShift==0){
				binShiftSamples = 0;
			}{binShiftSamples = ((windowSize/2)*binShift)-windowSizes.last};

			frames = (0..(numFrames-1)).collect{|frameNum|
				var tempArray;
				pointer = (fCNum*(chunkSize/durMult))+(frameNum*step)-(windowSize/2+binShiftSamples);
				//frameNum.post; " ".post; pointer.postln;
				if(pointer<0){
					tempArray = floatArray.copyRange(0, (pointer.floor+windowSize).asInteger);
					tempArray = FloatArray.fill(windowSize-tempArray.size, {0}).addAll(tempArray);
				}{
					pointer = pointer.floor;
					tempArray = floatArray.copyRange((pointer).asInteger, (pointer+windowSize-1).asInteger);
				};
				tempArray
			};

			if(fftType==0){
				frames = frames.collect{|frame| this.phaseRandoFFT(frame, lowBin, highBin, linkwitzRileyWindow, filterOrder)};
			}{
				frames = frames.clump(2);
				frames = frames.collect{|frame|
					if(frame.size==2){
						this.phaseRandoDualRFFT(frame[0], frame[1], lowBin, highBin, linkwitzRileyWindow, filterOrder);
					}{
						//only if the numFrames is odd
						//"odd man out".postln;
						[this.phaseRandoFFT(frame[0], lowBin, highBin, Signal.linkwitzRileyBP(windowSize/2, lowBin-1, highBin, filterOrder).addAll(Signal.fill(windowSize/2, {0})), filterOrder)]
					}
				}.flatten
			};

			frames.do{|arrayB, frameNum|
				smallArrays = [arrayA.copyRange((windowSize/2).asInteger, windowSize-1), arrayB.copyRange(0, (windowSize/2).asInteger-1)];

				if(smallArrays[0].sum!=0){
					correlation = (((smallArrays[0]*smallArrays[1]).sum)/((smallArrays[0]*smallArrays[0]).sum))}{correlation = 0};

				if(correlation.isNaN){correlation = 0};

				if(correlation<0){arrayB = arrayB.neg};

				fftWind = tanWindows[windowSize.asInteger.asSymbol][correlation.abs.linlin(0, 0.99, 0, 99).asInteger];
				addArray = ((smallArrays[0]*fftWind[1])+(arrayB.copyRange(0, (windowSize/2).asInteger-1)*fftWind[0]));
				addArray.do{|item, i|
					var index = (frameNum*(windowSize/2))+i;
					bigList.put(index, (item+bigList[index]))
				};
				arrayA = arrayB;
			};
			lastArrayA.put(num, arrayA);
		};

		"writeChunk".postln;
		sfFinal.writeData(bigList.as(FloatArray));

		^lastArrayA
	}

	*getServer{
		if(server==nil){
			serverNum = 57100+NRT_Server_Inc.next;
			while(
				{("lsof -i:"++serverNum++" ").unixCmdGetStdOut.size > 0},
				{serverNum = 57100+NRT_Server_Inc.next; serverNum.postln}
			);

			("server id: "++serverNum).postln;
			server = Server(("lang "++serverNum).asSymbol, NetAddr("127.0.0.1", serverNum),
				options: Server.local.options
			);
		};
	}

	*mkStretchTemp{|path, inFile, durMult=100, chanNum, splits = 9, filterOrder=129, fftType=0|
		var file = File(path, "w");

		file.write("TimeStretch.stretchChan("++inFile.quote++", "++durMult++", "++chanNum++","++splits++", "++filterOrder++","++fftType++");");
		file.close;
	}

	*stretch2PlusChannels {|inFile, durMult=100, chanArray=nil, splits = 9, filterOrder=129, fftType=0|
		var fileName = PathName(inFile).pathOnly++PathName(inFile).fileNameWithoutExtension;

		if(chanArray==nil){chanArray = Array.fill(SoundFile.openRead(inFile).numChannels, {|i| i})};

		chanArray.do{|chanNum, i2|
			TimeStretch.mkStretchTemp(fileName++"_"++chanNum++".scd", inFile, durMult, chanNum, splits, filterOrder, fftType);
			AppClock.sched((1), {("sclang "++fileName++"_"++chanNum++".scd").postln.runInTerminal});
		}
	}

	*stretchChan {|inFile, durMult=100, chanNum=0, splits = 9, filterOrder=129, fftType = 0|
		var winType=0, binShift=0, maxWindowSize = 65536, windowSizes;
		var chunkSize, chunkMul, temp, sfFinal, floatArray;

		var numSamplesToProcess, sf;
		var totalFrames, totalChunks, frameChunks, tempDir;
		var lastArrayA;
		var extension, time = Main.elapsedTime;

		if(splits.size==0){
			windowSizes = (maxWindowSize/(2**(0..8))).asInteger.copyRange(9-splits, 8).postln;
		}{
			windowSizes = splits.postln;
		};

		this.makeWindows(winType);
		this.makeFftCosTables;

		startTime = Main.elapsedTime;

		sf = SoundFile.openRead(inFile);

		numSamplesToProcess=sf.sampleRate*sf.duration;
		numSamplesToProcess.postln;

		totalFrames = numSamplesToProcess*durMult;

		chunkSize = 65536;

		totalChunks = (totalFrames/(chunkSize)).ceil;
		frameChunks = Array.fill(totalChunks, {chunkSize});

		"durMult: ".post; durMult.postln;
		"Processing Chunks: ".post; totalChunks.postln;
		"Frame Chunks: ".post; frameChunks.postln;

		floatArray = FloatArray.newClear(sf.numFrames*sf.numChannels);

		sf.readData(floatArray);

		temp = maxWindowSize-(sf.numFrames%maxWindowSize);

		floatArray = floatArray.clump(sf.numChannels).flop[chanNum].addAll(FloatArray.fill(temp+maxWindowSize, {0}));
		"FloatArray Size: ".post; floatArray.size.postln;

		if(sf.numFrames*durMult>(2**30)){extension="w64"}{extension="wav"};
		sfFinal = SoundFile.openWrite(PathName(inFile).pathOnly++PathName(inFile).fileNameWithoutExtension++"_long"++durMult++"_"++chanNum++"."++extension, extension, "float", 1, sf.sampleRate);

		lastArrayA = List.newClear(9);
		frameChunks.size.do{|fCNum|
			lastArrayA = this.processChunk(server, sfFinal, floatArray, chanNum, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType, binShift, filterOrder);
		};

		"all stretched! closing file: ".postln; sfFinal.path.postln;
		sfFinal.close;
		("Time to Stretch: "++(Main.elapsedTime-time)).postln;
	}


	*merge {|inFilesArray, outPath, numChans=2, transientMix=1|

		var files, channels, chunkSize = 6553600;
		var buffers, counter, doit, soundFiles, finalFile, extension;
		var temp, temp5;


		soundFiles = inFilesArray.collect{|file| SoundFile.openRead(file)};

		if(soundFiles[0].numFrames>(2**29)){extension="w64"}{extension="wav"};
		outPath = PathName(outPath).pathOnly++PathName(outPath).fileNameWithoutExtension++"."++extension;
		finalFile = SoundFile.openWrite(outPath, extension, "float", numChans, soundFiles[0].sampleRate);
		"expecting 2 or 4 files...full stretch or resonant files should be the first two files...transient files should be the second two".postln;"".postln;
		"for longer stretches, you may just want to use a DAW".postln;"".postln;
		"merge files!".postln;"".postln;
		"you will be done in ".post;(soundFiles[0].duration*soundFiles[0].sampleRate/chunkSize).post;" ticks".postln; "".postln;
		(soundFiles[0].duration*soundFiles[0].sampleRate/chunkSize).do{|i|
			".".post;
			temp = List.fill(soundFiles.size, {FloatArray.newClear(chunkSize)});

			numChans.do{|i2|
				soundFiles[i2].readData(temp[i2]);
				if(soundFiles.size>2){
					soundFiles[i2+2].readData(temp[i2+2]);
					temp.put(i2, temp[i2]+(temp[i2+2]*transientMix));
				};
			};

			temp5 = temp.copyRange(0,1).flop.flat.as(FloatArray);
			finalFile.writeData(temp5);
		};
		"".postln;
		"done - closing file".postln;
		finalFile.close;
	}


	 *transientSeparation {|inFile|
	 	var t1, t2, r1, r2, buf;
	 	var fileName;
	 	var resFileOut, transFileOut, tempDir;

	 	this.getServer;

	 	fileName = PathName(inFile).pathOnly++PathName(inFile).fileNameWithoutExtension;

	 	resFileOut = fileName++"_resonance.wav";
	 	if(transFileOut==nil){transFileOut = fileName++"_transients.wav"};

	 	server.waitForBoot{

	 		buf = Buffer.read(server, inFile, action:{|buffy|

	 			t1 = Buffer.new(server);
	 			r1 = Buffer.new(server);
	 			t2 = Buffer.new(server);
	 			r2 = Buffer.new(server);
	 			"separating".postln;
	 			FluidBufTransients.process(server, buffy, 0, -1, 0, -1, t1, r1, 40, 1024, 256, 10, 2, 1.1, 14, 25,
	 				action:{|trans, res|
	 					"separating again".postln;
	 					FluidBufTransients.process(server, res, 0, -1, 0, -1, t2, r2, 40, 512, 256, 10, 2, 1.1, 14, 25,
	 						action:{|trans2, res2|
	 							res2.write(resFileOut);
	 							FluidBufCompose.process(server, trans, destination:trans2, destGain:1, action:{|finTrans|
	 								{
	 									"putting back together".postln;
	 									finTrans.write(transFileOut);
	 									server.sync;
	 									server.quit;
	 									server = nil;
	 								}.fork;
	 							})
	 					})
	 			});
	 		});
	 	}
	 }


}
