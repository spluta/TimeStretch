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
	classvar <>tanWindows, <>fftCosTables, <>hannWindows, startTime;
	classvar serverNum, server;

	*phaseRandoFFT {|array, lowBin, highBin|
		var real, imag, cosTable, phase, rando, complex, complex2, ifft, size, hann, mags, mags2, bins, idft, real2, imag2, spectrum, phases2;

		size = array.size;
		bins = (size/2).asInteger;

		hann = hannWindows[size.asSymbol];

		real = array.as(Signal);
		real = real*hann;

		cosTable = fftCosTables[size.asSymbol];

		imag = Signal.newClear(size);

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

		hann = hannWindows[size.asSymbol];

		real = array.as(Signal);
		real = real*hann;

		cosTable = fftCosTables[size.asSymbol];

		complex = fft(real, nil, cosTable);

		mags = complex.magnitude.copyRange(0, (size/2).asInteger).asList;

		phases2 = complex.phase.copyRange(0, (size/2).asInteger).asList;

		mags2 = Array.fill(mags.size, {0});
		(lowBin..highBin).do{|i| mags2.put(i, mags[i]); phases2.put(i, pi.rand)};

		spectrum = Polar(mags2, phases2.asArray);

		ifft = spectrum.real.ifft(spectrum.imag, cosTable);
		^ifft.real
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

	*processChunk {|server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType=0, binShift = 0|
		var frameChunk = frameChunks[fCNum];
		var writeFile;
		var bigList = /*List.fill(chunkSize+(maxWindowSize*durMult), {0});*/List.fill(chunkSize, {0});
		var binShiftSamples;

		("Chan Num: "++chanNum).postln;
		("Chunk "++(fCNum)++" of "++(frameChunks.size-1)).postln;

		windowSizes.size.do{|num|
			var correlations, getNext, windowSize, step, windowEnv, fftWind, numFrames, smallArrays, addArray, correlation, lowBin, highBin, pointer, tempArray;
			var arrayA, arrayB;

			if(num==0){lowBin=0; highBin = 127}{lowBin=64; highBin = 127};
			lowBin.post;" ".post;highBin.postln;

			windowSize = windowSizes[num];
			"num ".post;
			num.postln;
			"winSize ".post;
			windowSize.postln;

			numFrames = frameChunk/(windowSize/2); //not right

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

			numFrames.do{|frameNum|
				pointer = (fCNum*(chunkSize/durMult))+(frameNum*step)-(windowSize/2+binShiftSamples);
				if(pointer<0){
					tempArray = floatArray.copyRange(0, (pointer.floor+windowSize).asInteger);
					tempArray = FloatArray.fill(windowSize-tempArray.size, {0}).addAll(tempArray);
				}{
					tempArray = floatArray.copyRange((pointer).asInteger, (pointer+windowSize-1).asInteger);
				};

				/*			numFrames.do{|frameNum|
				pointer = (fCNum*(chunkSize/durMult))+(frameNum*step);*/
				if(fftType==0){
					arrayB = this.phaseRandoRFFT(tempArray, lowBin, highBin);
				}{
					arrayB = this.phaseRando(tempArray, lowBin, highBin);
				};
				while({arrayB[0].isNaN}, {
					"array is NaN, going FFT".postln;
					arrayB = this.phaseRandoFFT(floatArray.copyRange((pointer).asInteger, (pointer+windowSize-1).asInteger), lowBin, highBin);
					arrayB.postln;
				});

				smallArrays = [arrayA.copyRange((windowSize/2).asInteger, windowSize-1), arrayB.copyRange(0, (windowSize/2).asInteger-1)];

				if(smallArrays[0].sum!=0){
					correlation = (((smallArrays[0]*smallArrays[1]).sum)/((smallArrays[0]*smallArrays[0]).sum))}{correlation = 0};

				if(correlation<0){arrayB = arrayB.neg};

				//if(correlation.abs>0.5){(correlation+correlation.abs.linlin(0, 0.99, 0, 99).asInteger).postln};
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

				this.processChunk(server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, fCNum, lastArrayA, serverNum, fftType, binShift);
			}{
				"doneWChannel".postln;
				NetAddr("127.0.0.1", NetAddr.langPort).sendMsg(("/"++serverNum).asSymbol, "process next chan");
			}
		});

	}

	*transientSeparation {|inFile, resFileOut, transFileOut|
		var t1, t2, r1, r2, buf;
		var fileName = PathName(inFile).pathOnly++PathName(inFile).fileNameWithoutExtension;

		this.getServer;

		if(resFileOut==nil){resFileOut = fileName++"_resonance.wav"};
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

	*getServer{
		if(server==nil){
			serverNum = 57110+NRT_Server_Inc.next;
			while(
				{("lsof -i:"++serverNum++" ").unixCmdGetStdOut.size > 0},
				{serverNum = 57110+NRT_Server_Inc.next; serverNum.postln}
			);

			("server id: "++serverNum).postln;
			server = Server(("lang "++serverNum).asSymbol, NetAddr("127.0.0.1", serverNum),
				options: Server.local.options
			);
		};
	}

	*mkStretchTemp{|path, inFile, outFolder, durMult=100, chanArray, startFrame=0, splits = 9, chunkSize = 6553600|
		var file = File(path, "w");

		file.write("TimeStretch.stretch("++inFile.quote++", "++outFolder.quote++", "++durMult++", "++chanArray++", 0, "++splits++", "++chunkSize++");");
		file.close;
	}

	*stretchResonAndTrans {|resFile, transFile, durMult=100, chanArray, startFrame=0, splits = 9, chunkSize = 6553600|

		if(splits.size==0){splits = splits.dup};

		[resFile, transFile].do{|which, i|
			var fileName = PathName(which).pathOnly++PathName(which).fileNameWithoutExtension;
			//fileName.postln;
			chanArray.do{|chan, i2|
				TimeStretch.mkStretchTemp(fileName++"_"++chan++".scd", fileName++".wav", fileName++"/", durMult, [chan], startFrame, splits[i], chunkSize);
				AppClock.sched((10*i2)+(20*i), {("sclang "++fileName++"_"++chan++".scd").postln.runInTerminal});
			}
		}
	}

	*stretch {|inFile, outFolder, durMult=100, chanArray, startFrame=0, splits = 9, chunkSize = 6553600|
		var fftType=0, winType=0, binShift=0, merge=0, maxWindowSize = 65536, windowSizes;

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

			if(chunkSize<1){
				chunkSize = totalFrames;
				totalChunks = 1;
				frameChunks = Array.fill(totalChunks.floor, {chunkSize});
			}{
				totalChunks = totalFrames/(chunkSize);
				frameChunks = Array.fill(totalChunks.floor, {chunkSize}).add(totalFrames-(totalChunks.floor*chunkSize));
			};

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
					this.processChunk(server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, startFrame, lastArrayA, serverNum, fftType, binShift);
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
							this.processChunk(server, tempDir, floatArray, chanNum, outFolder, windowSizes, maxWindowSize, durMult, chunkSize, frameChunks, 0, lastArrayA, serverNum, fftType, binShift);
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

	*merge {|folder, numChans=2|

		this.getServer;

		server.waitForBoot{
			var files, channels, chunkSize;


			var buffers, counter, doit;

			var finalBuf = Buffer(server);



			if(folder.last.asString!="/"){folder = folder++"/"};
			//folder.postln;
			folder = PathName(folder);

			files = folder.files.select{arg file; file.extension=="wav"};

			files = files.sort({arg a, b; var c, d;
				c = a.fileNameWithoutExtension;
				c = c.copyRange(c.findAll("_").last+1, c.size-1).asInteger;
				d = b.fileNameWithoutExtension;
				d = d.copyRange(d.findAll("_").last+1, d.size-1).asInteger;
				c<d});

			files.do{|file|file.postln};
			channels = numChans.collect{|chan| files.select{arg file; file.fullPath.contains("_"++chan++"_")}};

			chunkSize = SoundFile.openRead(files[0].fullPath).numFrames;

			"merge files!".postln;
			//FluidBufCompose.process(server, buffers[0][0], 0, -1, 0, -1, 1, finalBuf, 0, 0, 0,  action:{doit0.value(0, 0)});

			doit = {Routine{
				server.sync;
				buffers.do{|chan, i|
					chan.do{|buf, i2|
						//("chan:"+i+"buffer"+i2).postln;
						"merging buffers...may take a while".postln;
						FluidBufCompose.processBlocking(server, buf, 0, -1, 0, -1, 1, finalBuf, i2*chunkSize, i, 0, true, {("chan:"+i+"buffer"+i2).postln}).wait;

					};
				};
				server.sync;
				"write file".postln;
				//server.sync;
				finalBuf.query;
				server.sync;
				finalBuf.duration.postln; finalBuf.numChannels.postln;
				if((finalBuf.duration*finalBuf.numChannels)>5000){
					//"w64".postln;
					finalBuf.write(folder.parentPath++folder.folderName++".w64", "w64", "int24");
					server.quit;
				}{
					finalBuf.write(folder.parentPath++folder.folderName++".wav", "wav", "int24");
					server.quit;
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

	*mergeResonAndTrans {|outFile, resDir, transDir, numChans=2|

		this.getServer;

		server.waitForBoot{
			var files, filesArray, channels, chunkSize;


			var buffers, counter, doit, outType;

			var finalBuf = Buffer(server);
			var finalRes = Buffer(server);
			var finalTrans = Buffer(server);

			filesArray = [resDir, transDir].collect{|which, i|
				var folder = PathName(which).pathOnly++PathName(which).fileNameWithoutExtension;

				if(folder.last.asString!="/"){folder = folder++"/"};
				//folder.postln;
				folder = PathName(folder);

				files = folder.files.select{arg file; file.extension=="wav"};

				files = files.sort({arg a, b; var c, d;
					c = a.fileNameWithoutExtension;
					c = c.copyRange(c.findAll("_").last+1, c.size-1).asInteger;
					d = b.fileNameWithoutExtension;
					d = d.copyRange(d.findAll("_").last+1, d.size-1).asInteger;
					c<d});

				//files.do{|file|file.postln};
				channels = numChans.collect{|chan| files.select{arg file; file.fullPath.contains("_"++chan++"_")}};

				chunkSize = SoundFile.openRead(files[0].fullPath).numFrames;
				channels
			};

			//filesArray.size.postln;
			//filesArray[0].postln;
			//filesArray[1].postln;

			"merge files!".postln;

			doit = {
				Routine{
					server.sync;
					buffers.do{|filesArray, fileNum|
						filesArray.do{|chan, i|
							chan.do{|buf, i2|
								("fileNum:"+fileNum+"chan:"+i+"buffer"+i2).postln;
								"merging buffers...may take a while".postln;
								FluidBufCompose.processBlocking(server, buf, 0, -1, 0, -1, 1, finalBuf, i2*chunkSize, i, 1, true, {("chan:"+i+"buffer"+i2).postln}).wait;
								if(fileNum==0){
									FluidBufCompose.processBlocking(server, buf, 0, -1, 0, -1, 1, finalRes, i2*chunkSize, i, 1, true).wait;
								}{
									FluidBufCompose.processBlocking(server, buf, 0, -1, 0, -1, 1, finalTrans, i2*chunkSize, i, 1, true).wait;
								}
							};

						}
					};
					server.sync;
					"write file".postln;
					//server.sync;
					finalBuf.query;
					server.sync;
					//finalBuf.duration.postln; finalBuf.numChannels.postln;
					if((finalBuf.duration*finalBuf.numChannels)>5000){outType="w64"}{outType="wav"};

					finalBuf.write(PathName(outFile).pathOnly++PathName(outFile).fileNameWithoutExtension++"."++outType, outType, "int24");
					finalRes.write(PathName(resDir.copyRange(0, resDir.size-2)).pathOnly++PathName(resDir).folderName++"_long_resonance."++outType, outType, "int24");
					finalTrans.write(PathName(transDir.copyRange(0, transDir.size-2)).pathOnly++PathName(transDir).folderName++"_long_transients."++outType, outType, "int24");
					server.sync;
					server.quit;
			}.play};

			counter = 0;
			buffers = filesArray.collect{|channels, fileNum|
				channels.collect{|chan| chan.collect{|file, chanNum|
					//[fileNum,chanNum].postln;
					Buffer.read(server, file.fullPath, 0, -1, {
						counter = counter+1;
						if(counter==(files.size*2)){"buffers loaded".postln; doit.value};
					})
				}
				};
			}
		};
	}

}
