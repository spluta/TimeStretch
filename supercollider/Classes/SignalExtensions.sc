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
}
