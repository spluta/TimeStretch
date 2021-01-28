+ TimeStretch {

	*transientSeparation {|inFile|
		var t1, t2, r1, r2, buf;
		var fileName;
		var resFileOut, transFileOut, tempDir;

		this.getServer;

		tempDir = PathName(inFile).pathOnly++"tempDir/";

		if(tempDir.isFolder.not){("mkdir "++tempDir.escapeChar($ )).systemCmd};

		fileName = tempDir++PathName(inFile).fileNameWithoutExtension;

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

	*stretchResonAndTrans {|inFile, durMult=100, chanArray, startFrame=0, splits = 9, filterOrder=129|
		var resFile, transFile, tempDir;

		tempDir = PathName(inFile).pathOnly++"tempDir/";

		resFile = tempDir++PathName(inFile).fileNameWithoutExtension++"_resonance.wav";
		transFile = tempDir++PathName(inFile).fileNameWithoutExtension++"_transients.wav";

		if(splits.size==0){splits = splits.dup};

		[resFile, transFile].do{|which, i|
			var fileName = PathName(which).pathOnly++PathName(which).fileNameWithoutExtension;
			//fileName.postln;
			chanArray.do{|chan, i2|
				TimeStretch.mkStretchTemp(fileName++"_"++chan++".scd", fileName++".wav", fileName++"/", durMult, [chan], startFrame, splits[i], filterOrder);
				AppClock.sched((10*i2)+(20*i), {("sclang "++fileName++"_"++chan++".scd").runInTerminal});
			}
		}
	}

	*mergeResonAndTrans {|inFile, numChans=2|

		var outFile, resDir, transDir, tempDir;

		tempDir = PathName(inFile).pathOnly++"tempDir/";

		resDir = tempDir++PathName(inFile).fileNameWithoutExtension++"_resonance/";
		transDir = tempDir++PathName(inFile).fileNameWithoutExtension++"_transients/";

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
								var db;
								("fileNum:"+fileNum+"chan:"+i+"buffer"+i2).postln;
								"merging buffers...may take a while".postln;

								FluidBufCompose.processBlocking(server, buf, 0, -1, 0, -1, [1,0.707].at(fileNum), finalBuf, i2*chunkSize, i, 1, true, {("chan:"+i+"buffer"+i2).postln}).wait;
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
					if((finalBuf.duration*finalBuf.numChannels)>5000){outType="w64"}{outType="wav"};

					outFile = PathName(inFile).pathOnly++PathName(inFile).fileNameWithoutExtension;

					finalBuf.write(outFile++"_long."++outType, outType, "int24");
					finalRes.write(outFile++"_long_resonance."++outType, outType, "int24");
					finalTrans.write(outFile++"_long_transients."++outType, outType, "int24");
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