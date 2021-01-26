+ Signal {

	*linkwitzRileyLP {arg size, hiBin, order;
		var temp;
		temp = (0..size-1);
		temp = temp.collect{|item| 1/(1+((item/(hiBin)**order)))}
		^temp.as(Signal)
	}

	*linkwitzRileyHP {arg size, lowBin, order;
		var temp;
		temp = (0..size-1);
		if(lowBin!=0){
			temp = temp.collect{|item| 1-(1/(1+((item/lowBin)**order)))}
		}{temp = Signal.fill(size, {1})};
		^temp.as(Signal)
	}

	*linkwitzRileyBP {arg size, lowBin, hiBin, order;
		if(lowBin<=0){
			^this.linkwitzRileyLP(size, hiBin, order)
		}{
			if(hiBin>=(size-1)){^this.linkwitzRileyHP(size, lowBin, order)};
			^(this.linkwitzRileyLP(size, hiBin, order)*this.linkwitzRileyHP(size, lowBin, order));
		}
	}

	*sineFillWFreqs {|size = 512, freqs=93.75, sampleRate = 44100|
		var real, temp, sigSize, realz;
		var root = sampleRate/size;

		if(freqs.size==0){freqs = [freqs]};

		realz = freqs.collect{|freq|
			temp = freq/root;
			sigSize = temp.ceil/temp;
			real = Signal.sineFill((sigSize*size).postln, Array.fill(temp.ceil-1, {0}).add(1));
			real.copyRange(0, size-1)
		};
		^realz.sum;
	}
}
