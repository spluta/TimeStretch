+ FluidNMFStretch {

	getMFCCChannelParallel {|chanNum, chunkLength = 88200, waitTime = 5|
		var doit;
		var files, counter, oscy, folder, countTo;

		"getMFCCChannel ".post; chanNum.postln;

		folder = chanFolder++"Chan"++chanNum++"/";
		folder.postln;

		files = PathName(folder).files;
		files.size.postln;

		countTo = sf.numFrames/chunkLength-1;
		{
		files.do{|file, i|
			var mfccBuf, leBeuf, statsBuf, locServer, fileDataSet, id;



			file.postln;
			id = NRT_Server_ID.next;

			locServer = Server(id.asSymbol, NetAddr("127.0.0.1", id), Server.local.options);

			locServer.waitForBoot({

				fileDataSet = FluidDataSet(locServer, (chanNum.asString++"-"++i).asSymbol);
					3.wait;
				locServer.sync;
				oscy = OSCFunc({|msg|
					msg.post; id.postln;
					if(msg[2]==id){
						id.post;" Ended!".postln;
						fileDataSet.write(mfccFolder++"Chan"++chanNum++"File/"++file.fileNameWithoutExtension++".json", {
							"file written".postln;
							leBeuf.free;
							locServer.quit;
							oscy.free;
						}
						);
					}
				},'/tr');

				leBeuf = Buffer.read(locServer,file.fullPath.postln, action:{|audioBuf|

					var mfccBuf = Buffer.new(locServer);
					var statsBuf = Buffer.new(locServer);
						{
						2.wait;
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
						dsWr = FluidDataSetWr.kr(fileDataSet, buf: buf, trig: Done.kr(stats));
						LocalOut.kr(Done.kr(dsWr));
						endTrig = count - countTo;
						SendTrig.kr(endTrig, id);
						FreeSelf.kr(endTrig);
					}.play(locServer);
						}.fork;
				})
			});
				waitTime.wait;
		}
		}.fork
	}
}