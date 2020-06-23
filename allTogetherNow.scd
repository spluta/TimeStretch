s.reboot

//create an instance of FluidNMFStretch
//probably best to reboot the server before loading
f = FluidNMFStretch(s, "/Volumes/Samsung_T5/Mahler5Chailly/Mahler.wav")
f = FluidNMFStretch(s, "/Volumes/Samsung_T5/AdagiettoShort/AdagiettoShort.wav")
f = FluidNMFStretch(s, "/Volumes/Samsung_T5/AdaggietoSweden/AdagiettoHardingSweden.wav")

f = FluidNMFStretch(s, "/Volumes/Samsung_T5/Mahler3OhMench/Mahler3.wav")

//makes the necessary subfolders to hold the NMF Channels and the MFCC Channels
f.makeFolders;

//does the nmf component analysis, saves the N channel files in the Main folder and places each component of each channel of the NMF analysis in its own folder for inside the Chans folder. So, if the analyzed file is stereo and we analyze for 50 components, there will be 2 folders in the Chans folder, each with 50 files in them
f.nmf(50);  //this may take a while!!!

//time stretches each of the N separate NMF channels and places them in the user specified folder inside the Main folder. This folder will have as many folders as there are channels in the original file
//the stretch algorithms are run in non-real-time, so it will run all of the stretches at once
f.stretch(12, "Stretch12_Disp05", 8192, 0.05)

//allows the use of experimental algorithms for time stretching which use different bin sizes for higher pitched and lower pitched NMF channels. The algorithm relies upon a spectral centroid analysis of each NMF channel to decide which are higher and lower in pitch, so getCentroid must be run before variWindowStretch
f.getCentroid  //analyses the channels in parallel
f.variWindowStretch(12, "VariStretchDispAlg2C",0.05, 2)

//centroids can also be saved and reloaded
f.saveCentroid
f.loadCentroid

//the panning algorithm of FluidNMFStretch relies upon an MFCC analysis of the NMF channels, grouping the channels into clumps

//get MFCC does the MFCC analysis on each NMF channel, saving the analysis to the ChanNFile folders inside the mfcc folder
//at this point, these need to happen one channel at a time
//it will say "Buffer UGen: no buffer data". THIS IS A LIE!
//it will say "Processing Cancelled" but that just means it is done

f.getMFCCChannel(0)
f.getMFCCChannel(1)

//f.getMFCCChannelParallel(1, 88200, 5)

//the mfcc analysis only needs to happen once. after, the files can be loaded with loadFileDataSets
f.loadFileDataSets


//the MFCC analysis is in a format of corresponding to one per NMF file, but we need the format to be in frames (1 file per 2 second chunk of data), so that channels can be panned in similar groups. Thus, the data needs to be put into frame format
f.saveFrameDataSetsFromFile

//you may need to do these one channel at a time
f.saveFrameDataSetsFromFileChannel(0)
f.saveFrameDataSetsFromFileChannel(1)

//on each frame, the algorithm clusters the N time-stretched NMF channels into timbrel similarity
//to do this, we must create clusters
f.loadFrameDataSets
f.createClusters


f.saveClusterData //save these for future use
f.loadClusterData //and next time we can load them

//PLAYBACK
//now that the data has all been saved, we can load an run this at any point
//run each line independently
s.reboot
f = FluidNMFStretch(s, "/Volumes/Samsung_T5/AdaggietoSweden/AdagiettoHardingSweden.wav")
f.loadClusterData;
f.setStretchFolder("VariStretchDispAlg2C"); //choose the correct Stretch folder inside the Main folder (there can be multiple)
f.makePanner;
f.playAtSlice(50);

//todo!!!
deal with panning
--isolating groups
--moving pan points



