TimeStretch5 {
	classvar <>tanWindows, <>fftCosTables, <>hannWindows, startTime;

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

	/*	*phaseRandoRFFT {|array, lowBin, highBin|
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
	}*/



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

	*processChunk {|server, tempDir, frameChunks, fCNum, chanNum, outFolder, maxWindowSize, durMult, serverNum|
		var currentChunk = frameChunks[fCNum];
		var chunkSize = currentChunk.size;
		var bigList = List.fill(chunkSize+(maxWindowSize*durMult), {0});
		var writeFile;

		("Chan Num: "++chanNum).postln;
		("Chunk "++(fCNum)++" of "++(frameChunks.size-1)).postln;
		("ChunkSize "++chunkSize).postln;

		9.do{|num|
			var correlations, getNext, windowSize, step, windowEnv, fftWind, numFrames, smallArrays, addArray, correlation, lowBin, highBin, pointer;
			var arrayA, arrayB, tempArray;

			if(num==0){lowBin=0; highBin = 127}{lowBin=64; highBin = 127};
			lowBin.post;" ".post;highBin.postln;

			windowSize = (maxWindowSize/(2**num)).asInteger.postln;
			"num ".post;
			num.postln;
			"winSize ".post;
			windowSize.postln;

			numFrames = currentChunk/(windowSize/2);

			step = (windowSize/2)/durMult;
			"step ".post; step.postln;

			("chunkSize"+chunkSize).postln;
			numFrames = (chunkSize/step).floor;
			("numFrames "++numFrames).postln;

/*			if(fCNum==0){*/
				arrayA = Array.fill(windowSize, {0});
/*			}{
				arrayA = lastArrayA[num];
			};*/

			currentChunk = currentChunk.addAll(FloatArray.fill(windowSize, {0}));

			numFrames.do{|frameNum|
				pointer = (fCNum*(chunkSize/durMult))+(frameNum*step)-(windowSize/2);
				//pointer.postln;
				if(pointer<0){
					tempArray = currentChunk.copyRange(0, (pointer.floor+windowSize).asInteger);
					tempArray = FloatArray.fill(windowSize-tempArray.size, {0}).addAll(tempArray);
				}{
					tempArray = currentChunk.copyRange((pointer).asInteger, (pointer+windowSize-1).asInteger);
				};
				//tempArray.postln;
				/*
				if(fftType==0){*/
					arrayB = this.phaseRandoRFFT(tempArray, lowBin, highBin);
/*				}{
					arrayB = this.phaseRando(tempArray, lowBin, highBin);
				};*/
				while({arrayB[0].isNaN}, {
					"array is NaN, going FFT".postln;
					arrayB = this.phaseRandoFFT(tempArray, lowBin, highBin);
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
					bigList.put(index/*+(windowSize/2)*/, (item+bigList[index/*+(windowSize/2)*/]))
				};
				arrayA = arrayB;
			};
			//lastArrayA.put(num, arrayA);
		};

		writeFile = tempDir++PathName(outFolder).folderName++"_"++chanNum++"_"++fCNum++".wav";
		writeFile.postln;

		Buffer.loadCollection(server, bigList, 1, {|finalBuf0|
			"time: ".post;
			(Main.elapsedTime-startTime).postln;
			finalBuf0.write(writeFile);

			//lastArrayA.writeArchive(tempDir++"lastArrays/"++PathName(outFolder).folderName++"_"++chanNum++"_"++fCNum++".lastArray");
			fCNum = fCNum+1;
			if(fCNum<frameChunks.size){
				"next Chunk".postln;

				this.processChunk(server, tempDir, frameChunks, fCNum, chanNum, outFolder, maxWindowSize, durMult, serverNum);
			}{
				"doneWChannel".postln;
				NetAddr("127.0.0.1", NetAddr.langPort).sendMsg(("/"++serverNum).asSymbol, "process next chan");
			}
		});

	}

	*stretch {|inFile, outFolder, dur, durMult=100, chanNum=0|
		var serverNum, server, transBuf;

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
			var totalFrames, totalChunks, tempDir;
			var chanCount;

			this.makeWindows(0);
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

			totalFrames = numSamplesToProcess*durMult;

			transBuf = Buffer.new(server);
			Buffer.readChannel(server, inFile, 0, -1, [chanNum], {|buffer|
				buffer.postln;
				FluidBufTransientSlice.process(server, buffer, 0, -1, 0, 1, transBuf, 20, 512, 256, action:{|trans|
					trans.postln;
					trans.loadToFloatArray(action:{|transArray|
						buffer.loadToFloatArray(action:{|floatArray|
							var frameChunks = Array.newClear(transArray.size);
							floatArray = floatArray.addAll(FloatArray.fill(maxWindowSize*2, {0}));
							transArray = transArray.asInteger;

							floatArray.size.postln;
							floatArray.postln;

							transArray.size.postln;
							transArray.postln;

							transArray = transArray.add(floatArray.size-1);
							transArray.postln;

							floatArray = FloatArray.fill(maxWindowSize/2, {0}).addAll(floatArray);

							(transArray.size-1).do{|i|
								var temp;

								temp = floatArray.copyRange(transArray[i], (transArray[i+1]+(maxWindowSize/2)).asInteger);
								temp.postln;
								(0, 0.2..1).do{|mul, i2|temp = temp.put(temp.size-(i2+1), temp[temp.size-(i2+1)]*mul)};

								frameChunks.put(i, temp);
							};
							this.processChunk(server, tempDir, frameChunks, 0, chanNum, outFolder, maxWindowSize, durMult, serverNum);
						});
					})
				});
			});
		}
	}

	*mergeFiles {|folder, numChans=2|
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

}
