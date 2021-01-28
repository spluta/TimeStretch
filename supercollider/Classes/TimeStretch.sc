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

		mags = complex.magnitude.deepCopy.asList;

		if(filterOrder<129){
			mags2 = mags*linkwitzRileyWindow;
			phases2 = complex.phase.collect{|phase, i| if(mags2[i]>0){pi.rand}{phase}};
		}{
			mags2 = Array.fill(mags.size, {0});
			phases2 = complex.phase.deepCopy.asList;
			(lowBin..highBin).do{|i| mags2.put(i, mags[i]); phases2.put(i, pi.rand)};
		};
		^Polar(mags2, phases2.asArray);
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
						window = Signal.newClear(windowSize).waveFill({|fs|
							fs = (fs*pi/2).tan**2;
							fs*((1/(1+(2*fs*(correlation))+(fs**2))).sqrt)
						}, 0, 2);

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

	*processChunk {|server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType=1, binShift = 0, filterOrder=129|
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
			lowBin.post;" ".post;highBin.postln;


			windowSize = windowSizes[num];
			linkwitzRileyWindow = Signal.linkwitzRileyBP(windowSize/2+1, lowBin-1, highBin, filterOrder);/*.addAll(Signal.fill(windowSize/2, {0}));*/
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

			if(binShift==0){
				binShiftSamples = 0;
			}{binShiftSamples = ((windowSize/2)*binShift)-windowSizes.last};

			frames = (0..(numFrames-1)).collect{|frameNum|
				var tempArray;
				pointer = (fCNum*(chunkSize/durMult))+(frameNum*step)-(windowSize/2+binShiftSamples);
				if(pointer<0){
					tempArray = floatArray.copyRange(0, (pointer.floor+windowSize).asInteger);
					tempArray = FloatArray.fill(windowSize-tempArray.size, {0}).addAll(tempArray);
				}{
					tempArray = floatArray.copyRange((pointer).asInteger, (pointer+windowSize-1).asInteger);
				};
				tempArray
			};

			frames = frames.clump(2);
			frames = frames.collect{|frame| this.phaseRandoDualRFFT(frame[0], frame[1], lowBin, highBin, linkwitzRileyWindow, filterOrder)}.flatten;

			frames.do{|arrayB, frameNum|

				smallArrays = [arrayA.copyRange((windowSize/2).asInteger, windowSize-1), arrayB.copyRange(0, (windowSize/2).asInteger-1)];

				if(smallArrays[0].sum!=0){
					correlation = (((smallArrays[0]*smallArrays[1]).sum)/((smallArrays[0]*smallArrays[0]).sum))}{correlation = 0};

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

		writeFile = tempDir++PathName(outFolder).folderName++"_"++chanNum++"_"++fCNum++".wav";
		writeFile.postln;

		Buffer.loadCollection(server, bigList, 1, {|finalBuf0|
			"time: ".post;
			(Main.elapsedTime-startTime).postln;
			finalBuf0.write(writeFile);

			lastArrayA.writeArchive(tempDir++"lastArrays/"++PathName(outFolder).folderName++"_"++chanNum++"_"++fCNum++".lastArray");
			fCNum = fCNum+1;
			if(fCNum<frameChunks.size){
				"next Chunk".postln;

				this.processChunk(server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType, binShift, filterOrder);
			}{
				"doneWChannel".postln;
				NetAddr("127.0.0.1", NetAddr.langPort).sendMsg(("/"++serverNum).asSymbol, "process next chan");
			}
		});

	}

	*getServer{
		if(server==nil){
			serverNum = 57110+NRT_Server_Inc.next;
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

	*mkStretchTemp{|path, inFile, outFolder, durMult=100, chanArray, startFrame=0, splits = 9, filterOrder=129|
		var file = File(path, "w");

		file.write("TimeStretch.stretch("++inFile.quote++", "++outFolder.quote++", "++durMult++", "++chanArray++", 0, "++splits++", "++filterOrder++");");
		file.close;
	}

	*stretch2PlusChannels {|inFile, durMult=100, chanArray, startFrame=0, splits = 9, filterOrder=129|
		var fileName = PathName(inFile).pathOnly++PathName(inFile).fileNameWithoutExtension;

		if(chanArray==nil){chanArray = Array.fill(SoundFile.openRead(inFile).numChannels, {|i| i})};

		chanArray.do{|chan, i2|
			TimeStretch.mkStretchTemp(fileName++"_"++chan++".scd", inFile, fileName++"_"++durMult++"/", durMult, [chan], startFrame, splits, filterOrder);
			AppClock.sched((10*i2), {("sclang "++fileName++"_"++chan++".scd").postln.runInTerminal});
		}
	}

	*stretch {|inFile, outFolder, durMult=100, chanArray, startFrame=0, splits = 9, filterOrder=129|
		var fftType=1, winType=0, binShift=0, merge=0, maxWindowSize = 65536, windowSizes;
		var chunkSize = 6553600, temp;
		this.getServer;

		if(splits.size==0){
			windowSizes = (maxWindowSize/(2**(0..8))).asInteger.copyRange(9-splits, 8).postln;
		}{
			windowSizes = splits.postln;
		};

		server.waitForBoot{
			var numSamplesToProcess, sf;
			var totalFrames, totalChunks, frameChunks, tempDir;
			var lastArrayA, chanNum, chanCount;

			this.makeWindows(winType);
			this.makeFftCosTables;

			startTime = Main.elapsedTime;

			if(outFolder.last.asString!="/"){outFolder=outFolder++"/"};

			tempDir = (PathName(outFolder).pathOnly).standardizePath;


			if(PathName(tempDir).isFolder.not){("mkdir "++tempDir.escapeChar($ )).systemCmd};
			if(PathName(tempDir++"lastArrays/").isFolder.not){("mkdir "++(tempDir++"lastArrays/").escapeChar($ )).postln.systemCmd};

			sf = SoundFile.openRead(inFile);

			numSamplesToProcess=sf.sampleRate*sf.duration;
			numSamplesToProcess.postln;

			chanArray = chanArray ?? Array.fill(sf.numChannels, {|i| i});

			totalFrames = numSamplesToProcess*durMult;


			totalChunks = (totalFrames/(chunkSize)).ceil;
			frameChunks = Array.fill(totalChunks, {chunkSize});

			"Processing Chunks: ".post; totalChunks.postln;
			"Frame Chunks: ".post; frameChunks.postln;

			chanCount = 0;
			if(startFrame==0){
				lastArrayA = List.newClear(9);
			}{
					var file;
					("loading last array from frame"++startFrame).post;
					file = (tempDir++"lastArrays/"++PathName(outFolder).folderName++"_"++chanArray[0]++"_"++(startFrame-1)++".lastArray").postln;
					file.postln;
					lastArrayA = Object.readArchive(file);
			};


			chanNum = chanArray[0];

			Buffer.readChannel(server, inFile, 0, -1, [chanNum], {|buffer|
				buffer.loadToFloatArray(action:{|floatArray|
					temp = maxWindowSize-(floatArray.size%maxWindowSize);
					floatArray = floatArray.addAll(FloatArray.fill(temp+maxWindowSize, {0}));
					this.processChunk(server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, startFrame, lastArrayA, serverNum, fftType, binShift, filterOrder);
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
							temp = maxWindowSize-(floatArray.size%maxWindowSize);
							floatArray = floatArray.addAll(FloatArray.fill(temp+maxWindowSize, {0}));
							this.processChunk(server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, 0, lastArrayA, serverNum, fftType, binShift, filterOrder);
						});
					});
				}{
					"we're done".postln;
					if(merge==1){
						this.mergeFiles(outFolder, sf.numChannels);
					}{
						server.quit;
						server = nil;
					}
				}
			}, ("/"++serverNum).asSymbol);
		}
	}



}
