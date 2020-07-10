b = Buffer.readChannel(s, "/Users/spluta/Library/Group Containers/2E337YPCZY.airmail/Library/Application Support/it.bloop.airmail2/Airmail/spluta@gmail.com_1/AttachmentsNg/CABL__EuqXBp2VRZS4eJCXzNTPtwoaoQ5myPCigk2YrT-ykRwvQ@mail.gmail.com/charli-xcx_blame.wav", channels:[0])

c = Buffer.readChannel(s, "/Users/spluta/Library/Group Containers/2E337YPCZY.airmail/Library/Application Support/it.bloop.airmail2/Airmail/spluta@gmail.com_1/AttachmentsNg/CABL__EuqXBp2VRZS4eJCXzNTPtwoaoQ5myPCigk2YrT-ykRwvQ@mail.gmail.com/charli-xcx_blame.wav", channels:[1])


TimeStretch.stretch(s, b, 0, -1, 100);
TimeStretch.stretch(s, c, 0, 1, 100);

TimeStretch.stop