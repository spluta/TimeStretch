FluidEqualSlicer {
	var <>index;

	slice {|buffer, indexIn, chunkSize = 44100|
		index = ();
		[buffer, indexIn].postln;
		indexIn.keys.do{|key|
			var parent=indexIn[key], frames, bounds = [0,0], label;

			[key, parent].postln;
			frames = parent['bounds'][1]-parent['bounds'][0];

			(frames/chunkSize).ceil.asInteger.do{|i|
				var dict = IdentityDictionary(), lilDict, chunkPoint;

				lilDict = parent.deepCopy;
				chunkPoint = i*chunkSize+parent['bounds'][0];
				lilDict.put('bounds', [chunkPoint, min(chunkPoint+chunkSize-1,parent['bounds'][1])]);
				index.put((key++"-"++(i+1)).asSymbol, lilDict);
			}
		};
	}
}

FluidNMFStretch {
	var <>server, <>fileIn, <>writeDir;
	var <>fileDataSets, <>frameDataSets, numChannels, <>chanFolder, <>mfccFolder, <>stretchFolder;
	var sf, <>clusterData, <>kmeans, <>centroids, <>vbapPanPoints, <>vbapPlayback, <>vbapMaps;

	*new {arg server, fileIn, writeDir;
		^super.new.server_(server).fileIn_(fileIn).writeDir_(writeDir).init;
	}

	init {

		server.waitForBoot{
			var buf, bufI=0;
			"server booted".postln;
			sf = SoundFile.openRead(fileIn);
			numChannels = sf.numChannels.postln;

			fileDataSets = List.newClear(numChannels);
			frameDataSets = List.newClear(numChannels);
			clusterData = List.newClear(numChannels);
			centroids = List.newClear(numChannels);
			kmeans = FluidKMeans.new(server);
			//this.clearDataSets;
			//TimeStretch.new;
		};
		if(writeDir==nil){
			writeDir = PathName(fileIn).pathOnly++"Main/";
			("mkdir "++writeDir).systemCmd;
		}{
			if(writeDir.isFolder.not){("mkdir "++writeDir).systemCmd};
		};
		chanFolder = writeDir++"Chans/";
		//stretchFolder = writeDir++"Stretch/";
		mfccFolder = writeDir++"mfcc/";
	}

	makeFolders {
		("mkdir "++chanFolder).systemCmd;
		//("mkdir "++stretchFolder).systemCmd;
		("mkdir "++mfccFolder).systemCmd;
		numChannels.do{|i|
			("mkdir "++chanFolder++"/Chan"++i++"/").systemCmd;
			//("mkdir "++stretchFolder++"/Chan"++i++"/").systemCmd;
			("mkdir "++mfccFolder++"/Chan"++i++"File/").systemCmd;
			("mkdir "++mfccFolder++"/Chan"++i++"Frame/").systemCmd;
		};

	}

	nmf {|components = 50, action|
		var paths, counter = 0, numBootedServers;

		paths = List.newClear(0);

		//

		numChannels.do{|bufI|
			var bufChan, resynth;
			"Chan ".post; bufI.postln;
			//this.makeFolders;
			resynth = Buffer.new(server);
			bufChan = Buffer.readChannel(server,fileIn, channels:[bufI],
				action:{|bufChan|
					[bufI,bufChan].postln;
					FluidBufNMF.process(server, bufChan, resynth:resynth, components: components, iterations:500, windowSize:2048,
						action:{|buf|
							buf.postln;
							buf.write(writeDir++PathName(fileIn).fileNameWithoutExtension++"_Chan"++bufI++"_"++components++".caf", "caf", "int24");
							//extractChannels.value(buf, bufI);

							buf.numChannels.do{|i|
								var local;
								local = Buffer.new(server);
								FluidBufCompose.process(server, buf, 0, -1, i, 1, destination:local, action:{arg singleBuf;
									var path,labelNum, bufName, mfccBuf, folder;
									labelNum = i.asString;
									(4-labelNum.size.postln).do{labelNum=labelNum.insert(0, "0")};
									bufName = PathName(fileIn).fileNameWithoutExtension++"_Chan"++bufI++"_"++labelNum;
									path = writeDir++"Chans/Chan"++bufI++"/"++bufName++".wav";

									singleBuf.write(path);

									counter = counter+1;
									counter.postln;
									if(counter==(numChannels*components)){

										"all NMFed".postln;
										action.value;

									}
								})
							};
					})
			});
		};

		//}.fork
	}

	stretch {|durMult=12, stretchFolderIn="Stretch", fftSize=8192, maxDispersion=0|
		var inFiles, x, chanFolders, folder;
		//[folderOrFile, durMult, stretchFolder].postln;
		{

			stretchFolder = writeDir++stretchFolderIn++"/";
			("mkdir "++stretchFolder).systemCmd;
			numChannels.do{|i|
				("mkdir "++stretchFolder++"/Chan"++i++"/").systemCmd;
			};

			chanFolders = PathName(chanFolder).folders;

			chanFolders.do{|folder, chanNum|
				var inFiles;

				inFiles = folder.files;
				inFiles.size.postln;
				inFiles.do{|inFile,i|
					var outFile;
					outFile = stretchFolder++folder.folderName++"/"++(inFile.fileName);
					TimeStretch.stretchNRT(inFile, outFile, durMult, fftSize, 2, rrand(0,maxDispersion));
				}
			}
	}.fork}

	makePanner {
		var map, temp;

		vbapMaps = List.fill(clusterData.size, {|chan|
			temp = List.newClear(0);
			clusterData[chan].size.do{|i|
				map = ();
				10.do{|i| map.put(i.asSymbol, i)};
				temp.add(map);
			};
			temp
		});

		vbapPanPoints = [VBAPPanPoints(-1), VBAPPanPoints(1)];
		vbapPlayback = List.fill(2, {|i|
			VBAPPlayback(Group.tail(server), PathName(stretchFolder++"/""Chan"++i++"/"), vbapPanPoints[i])
		});
	}

	saveVBAPMaps {|fileName = "vbapMaps"|
		fileName = writeDir++fileName;
		vbapMaps.writeArchive(fileName);
	}

	loadVBAPMaps {|fileName = "vbapMaps"|
		fileName = writeDir++fileName;
		vbapMaps = Object.readArchive(fileName);
	}

	playAtSlice {|sliceNum, chan|
		var framesPerSlice = vbapPlayback[0].soundFile.numFrames/clusterData[0].size;
		var waitTime = vbapPlayback[0].soundFile.duration/clusterData[0].size;

		if(chan==nil) {chan = (0..(vbapPlayback.size-1))};
		chan.postln;
		"Slice Num: ".post; sliceNum.postln;

		if(sliceNum>(clusterData[0].size-1)){"there are not that many slices".postln}{
			chan.do{|i|
				var tempDict = ();
				var vbap = vbapPlayback[i];
				clusterData[i][sliceNum].keys.do{|key| clusterData[i][sliceNum][key].do{|item| tempDict.put(item.asInteger.asSymbol, key)}};
				vbap.quePlayback(sliceNum*framesPerSlice, tempDict)
			};
			Routine({
				1.wait;
				chan.do{|i|
					"play ".post;Main.elapsedTime.postln;
					vbapPlayback[i].startPlayback;
				};
				((sliceNum+1)..(clusterData[0].size-1)).do{|sliceNum|
					server.sync;
					"Slice Num: ".post; sliceNum.postln;
					chan.do{|i|
						var tempDict = ();
						var vbap = vbapPlayback[i];
						clusterData[i][sliceNum].keys.do{|key| clusterData[i][sliceNum][key].do{|item| tempDict.put(item.asInteger.asSymbol, key)}};
						vbap.setPanning(tempDict, waitTime.postln);
					};
					waitTime.wait;
				};
			}).play
		}
	}

	variWindowStretch {|durMult=12, stretchFolderIn="Stretch", maxDispersion=0, variAlgorithm=1, splitPoints|
		var inFiles, x, chanFolders, folder, tupletDict, fftSize;
		if(splitPoints==nil){splitPoints = [700,1200, 1700,2400,3500]};

		if(centroids[0]==nil){"calculate centroids first"}{
			{
				stretchFolder = writeDir++stretchFolderIn++"/";
				("mkdir "++stretchFolder).systemCmd;
				numChannels.do{|i|
					("mkdir "++stretchFolder++"/Chan"++i++"/").systemCmd;
				};

				chanFolders = PathName(chanFolder).folders;

				chanFolders.do{|folder, chanNum|
					var inFiles;

					inFiles = folder.files;

					inFiles.size.postln;
					//NRT_TimeStretch.new;
					inFiles.do{|inFile,i|
						var outFile, centroid, temp, frameTuplet, disp;

						centroid=centroids[chanNum][i];
						disp = maxDispersion;

						temp = splitPoints.collect{|item| centroid>item};
						temp.postln;
						temp = temp.asInteger.sum;
						switch(variAlgorithm,
							0, {
								switch(temp,
									0, {fftSize = 8192*2; frameTuplet = 2},
									1, {fftSize = 8192*2; frameTuplet = 5},
									2, {fftSize = 8192; frameTuplet = 2},
									3, {fftSize = 8192; frameTuplet = 5},
									4, {fftSize = 8192; frameTuplet = 3},
									5, {fftSize = 4096; frameTuplet = 2}
								);
							},
							1, {
								switch(temp,
									0, {fftSize = 8192; frameTuplet = 2},
									1, {fftSize = 8192; frameTuplet = 2},
									2, {fftSize = 8192; frameTuplet = 2},
									3, {fftSize = 8192; frameTuplet = 2},
									4, {fftSize = 4096; frameTuplet = 5},
									5, {fftSize = 4096; frameTuplet = 3}
								);
							},
							2, {
								switch(temp,
									0, {fftSize = 8192*2; frameTuplet = 2},
									1, {fftSize = 8192; frameTuplet = 2},
									2, {fftSize = 8192; frameTuplet = [2,5].choose},
									3, {fftSize = 8192; frameTuplet = [2,5].choose},
									4, {fftSize = 4096; frameTuplet = 2; disp = maxDispersion/2},
									5, {fftSize = 4096; frameTuplet = 2; disp = maxDispersion/2}
								);
							}

						);
						[centroid, temp, fftSize, frameTuplet].postln;
						outFile = stretchFolder++folder.folderName++"/"++(inFile.fileName);
						TimeStretch.stretchNRT(inFile, outFile, durMult, fftSize, frameTuplet, rrand(0,disp));
					}
				}
		}.fork}
	}


	loadFileDataSets {
		fileDataSets.do{|item| item.do{|item2|item2.free}};
		numChannels.do{|chan|
			this.loadFileDataSetsChan(chan);
		}
	}

	loadFileDataSetsChan {|chanNum|
		var temp = List.newClear(0);
		fileDataSets[chanNum].do{|item|item.free};
		//fileDataSets = List.fill(numChannels, {List.newClear(0)});
		//numChannels.do{|i|
		PathName(mfccFolder++"/Chan"++chanNum++"File/").files.do{|file, i|
			temp.add(FluidDataSet(server, file.fileNameWithoutExtension).read(file.fullPath))
			//}
		};
		fileDataSets.put(chanNum, temp);
	}

	loadFrameDataSets {
		frameDataSets.do{|item| item.do{|item2|item2.free}};
		numChannels.do{|chan|
			this.loadFrameDataSetsChan(chan);
		}
	}

	loadFrameDataSetsChan {|chanNum|
		var temp = List.newClear(0);

		frameDataSets[chanNum].do{|item|item.free};

		PathName(mfccFolder++"/Chan"++chanNum++"Frame/").files.size.postln;

		PathName(mfccFolder++"/Chan"++chanNum++"Frame/").files.do{|file, i|
			//file.postln;
			temp.add(FluidDataSet(server, chanNum.asString++"-"++file.fileNameWithoutExtension).read(file.fullPath))
		};
		frameDataSets.put(chanNum, temp);
	}

	saveClusterData {|fileName = "clusters"|
		fileName = writeDir++fileName;
		clusterData.writeArchive(fileName);
	}

	loadClusterData {|fileName = "clusters"|
		fileName = writeDir++fileName;
		clusterData = Object.readArchive(fileName);
	}

	createClusters {
		numChannels.do{|chan|
			this.createClusterChan(chan, 10);
		}
	}

	setStretchFolder {|folder|
		stretchFolder = writeDir++folder;
	}

	createClusterChan {|chanNum, numClusters=10|
		var frameData, temp;

		frameData = frameDataSets[chanNum];
		if(frameData==nil){"load frame data first!".postln;}
		{
			temp = List.newClear(frameData.size);
			frameData[0].size({|numAudioFiles|
				numAudioFiles = numAudioFiles.asInteger.postln;
				{
					frameData.do{|frame, frameNum|
						var frameClusters, groups;

						[frame, frameNum].postln;
						groups = Dictionary();
						numClusters.do{|i| groups.put(i.asSymbol, List[])};
						groups.postln;
						frameClusters = FluidLabelSet(server, ("kmeans"++chanNum++"-"++frameNum).asSymbol);
						//frameClusters.postln;
						server.sync;
						kmeans.fitPredict(frame, frameClusters, numClusters, action: {|c|
							{
								numAudioFiles.do{|i|
									var labelNum;
									labelNum = i.asString;
									(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};

									//labelNum.postln;
									frameClusters.getLabel(labelNum, {|val|
										groups[val.asSymbol].add(labelNum)
									});
									server.sync
								};
								temp.put(frameNum, groups);

							}.fork
						});
						0.2.wait;
					};
					temp.postln;
					clusterData.put(chanNum, temp);
					server.sync;
				}.fork
			})
		}
	}

	getMFCC {|chunkLength = 88200|
		var numChanFolders = PathName(chanFolder).folders.size;
		numChanFolders.do{|chanNum|
			this.getMFCCChannel(chanNum, chunkLength);
		}
	}

	getMFCCChannel {|chanNum, chunkLength = 88200|
		var doit;
		var files, counter, oscy, folder, leBeuf, countTo;

		"getMFCCChannel ".post; chanNum.postln;

		folder = chanFolder++"Chan"++chanNum++"/";
		folder.postln;

		files = PathName(folder).files;
		files.size.postln;

		//sf = SoundFile(files[0].fullPath);
		fileDataSets.do{|item| item.do{item.free}};
		frameDataSets.do{|item| item.do{item.free}};

		fileDataSets.put(chanNum, List.fill(files.size, {|i| FluidDataSet(server, (chanNum.asString++"-"++i).asSymbol)}));

		countTo = sf.numFrames/chunkLength-1;

		doit = {|file, i|
			var mfccBuf, statsBuf;

			file.postln;

			leBeuf = Buffer.read(server,file.fullPath.postln, action:{|audioBuf|

				var mfccBuf = Buffer.new(server);
				var statsBuf = Buffer.new(server);
				audioBuf.postln;
				{

					var trig, buf, count, mfcc, stats, rd, wr1, dsWr, endTrig;

					trig = LocalIn.kr(1, 1);
					buf =  LocalBuf(19, 1);
					count = (PulseCount.kr(trig, 0) - 1);
					mfcc = FluidBufMFCC.kr(audioBuf, count*chunkLength, chunkLength, features:mfccBuf, numCoeffs:20, trig: trig);
					stats = FluidBufStats.kr(mfccBuf, 0, -1, 1, 19, statsBuf, trig: Done.kr(mfcc));
					rd = BufRd.kr(19, statsBuf, DC.kr(0), 0, 1);// pick only mean pitch and confidence
					wr1 = Array.fill(19, {|i| BufWr.kr(rd[i], buf, DC.kr(i))});
					dsWr = FluidDataSetWr.kr(fileDataSets[chanNum][i], buf: buf, trig: Done.kr(stats));
					LocalOut.kr(Done.kr(dsWr));
					endTrig = count - countTo;
					SendTrig.kr(endTrig, chanNum);
					FreeSelf.kr(endTrig);
				}.play;
			})
		};

		counter = 0;
		doit.value(files[counter], counter);

		oscy = OSCFunc({|msg|
			msg.postln;
			if(msg[2]==chanNum){
				"Ended!".postln;
				leBeuf.free;
				counter = counter+1;
				counter.postln;
				if(files.size>counter) {
					"again, again!".postln;
					doit.value(files[counter], counter)
				}
				{
					//"shaping to time sets".postln;
					oscy.free;

					//save and set
					this.saveFileDataSets(chanNum);
				}
			}
		},'/tr')
	}

	saveCentroid {|fileName = "centroids"|
		fileName = writeDir++fileName;
		centroids.writeArchive(fileName);
	}

	loadCentroid {|fileName = "centroids"|
		fileName = writeDir++fileName;
		centroids = Object.readArchive(fileName);
	}

	getCentroid {
		var numChanFolders = PathName(chanFolder).folders.size;
		centroids = List.newClear(numChanFolders);
		numChanFolders.do{|chanNum|
			this.getCentroidChannel(chanNum);
		}
	}

	getCentroidChannel {|chanNum|
		var files, centroidChan, folder, counter = 0;

		"getCentroid ".post; chanNum.postln;

		folder = chanFolder++"Chan"++chanNum++"/";
		folder.postln;

		files = PathName(folder).files;
		files.size.postln;
		centroidChan = List.newClear(files.size);

		Routine({
			files.do{|file, i|
				var buffer, ssBuf, statsBuf;
				buffer = Buffer.read(server, file.fullPath, action:{|buf|
					ssBuf = Buffer(server);
					statsBuf = Buffer(server);
					FluidBufSpectralShape.process(server, buf, features:ssBuf, action:{|features|
						FluidBufStats.process(server, features, 0, -1, 0, 1, statsBuf, action:{|stats|
							stats.loadToFloatArray(action:{|array|
								[i, array[0]].postln;
								centroidChan.put(i, array[0]);
								features.free;
								stats.free;
								buf.free;
								counter = counter+1;
								if(counter==files.size){
									"done".postln;
									centroids.put(chanNum, centroidChan);
								};
							})
						})
					});
				});
				server.sync;
				0.5.wait;
			};
			//server.sync;

		}).play
	}

	saveFileDataSets {|chanNum|
		fileDataSets[chanNum].do{|item, i|
			var labelNum;
			labelNum = i.asString;
			(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};
			item.write(mfccFolder++"Chan"++chanNum++"File/"++labelNum++".json")
		};
	}

	saveFrameDataSetsFromFile {
		numChannels.do{|i| this.saveFrameDataSetsFromFileChannel(i)}
	}

	saveFrameDataSetsFromFileChannel {|chanNum|
		fileDataSets[chanNum][0].size({|val|
			val.postln;
			frameDataSets.put(chanNum, Array.fill(val.asInteger, {|i| FluidDataSet.new(server, ("rotated"++chanNum++"_"++i).asSymbol)}));
		});

		fileDataSets[chanNum][0].size({|val|
			Routine({
				val.asInteger.do{|point|
					point = (point.asInteger);
					point.postln;
					0.01.wait;
					fileDataSets[chanNum].do{|dataSet, i|
						var labelNum, buf, label, localPoint;
						//[dataSet, i].postln;
						0.01.wait;≥
						labelNum = i.asString;
						(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};
						label = (labelNum++point).asSymbol;
						localPoint = point.asSymbol;
						buf = Buffer.new(server);

						dataSet.getPoint(localPoint, buf, action:{
							var temp;
							//buf.postln;
							frameDataSets[chanNum][point].addPoint(labelNum, buf, {buf.free});
							//buf.free;
						});
					};

				};
				"write files".postln;
				{
					frameDataSets[chanNum].do{|item, i|
						var labelNum;
						0.05.wait;
						labelNum = i.asString;
						(4-labelNum.size).do{labelNum=labelNum.insert(0, "0")};
						item.write(mfccFolder++"Chan"++chanNum++"Frame/"++labelNum++".json")
					};
				}.fork
			}).play;
		})

	}
}
