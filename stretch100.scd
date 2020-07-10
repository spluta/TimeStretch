b = Buffer.readChannel(s, "/Users/spluta/Library/Group Containers/2E337YPCZY.airmail/Library/Application Support/it.bloop.airmail2/Airmail/spluta@gmail.com_1/AttachmentsNg/CABL__EuqXBp2VRZS4eJCXzNTPtwoaoQ5myPCigk2YrT-ykRwvQ@mail.gmail.com/charli-xcx_blame.wav", channels:[0])

c = Buffer.readChannel(s, "/Users/spluta/Library/Group Containers/2E337YPCZY.airmail/Library/Application Support/it.bloop.airmail2/Airmail/spluta@gmail.com_1/AttachmentsNg/CABL__EuqXBp2VRZS4eJCXzNTPtwoaoQ5myPCigk2YrT-ykRwvQ@mail.gmail.com/charli-xcx_blame.wav", channels:[1])


TimeStretch.stretch(s, b, 0, -1, 100);
TimeStretch.stretch(s, c, 0, 1, 100);

TimeStretch.stop


//NMF stretch version


f = FluidNMFStretch(s, "/Volumes/StretchFiles/Charli/charli_blame.wav")

f.makeFolders  //make the enclosing folder

f.nmf(50);  //calculate and save the NMF channels

f.getCentroid  //get the centroid for the variStretch
f.variWindowStretch(100, "Stretch100", 0.05, 2)  //this may melt your computer