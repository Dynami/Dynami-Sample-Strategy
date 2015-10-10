/*
 * Copyright 2015 Alessandro Atria - a.atria@gmail.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sample.strategy;

import org.dynami.core.Event;
import org.dynami.core.IDynami;
import org.dynami.core.IStage;
import org.dynami.core.config.Config.Param;
import org.dynami.core.data.Series;
import org.dynami.core.orders.MarketOrder;
import org.dynami.ta.momentum.Rsi;
import org.dynami.ta.overlap_studies.MovingAverage;

import com.tictactec.ta.lib.MAType;

/**
 * Implementation in Dynami of RSI2 strategy developed by Larry Connors.
 * {@link http://stockcharts.com/school/doku.php?id=chart_school:trading_strategies:rsi2}
 */
public class Rsi2 implements IStage {
	// Declare strategy parameters
	@Param(description="Main asset symbol") 
	String symbol = "FTSEMIB";
	
	@Param(description="Long term moving average period")
	int longTermPeriod = 21; //200
	@Param(description="Short term moving average period")
	int shortTermPeriod = 10; //5
	@Param(description="RSI period")
	int rsiPeriod = 2; // 2
	
	double shortThreshold = 95; // 95
	double longThreshold = 5; // 5
	
	
	// Declare technical indicators and other stuffs
	MovingAverage longTerm; 
	MovingAverage shortTerm;
	Rsi rsi;
	
	Series close = new Series();

	@Override
	public void setup(IDynami dynami) {
		// It's recommended using setup method to initialize indicators, in order to setup parameters from external configuration file (future implementation)
		longTerm = new MovingAverage(longTermPeriod, MAType.Sma);
		shortTerm = new MovingAverage(shortTermPeriod, MAType.Ema);
		rsi = new Rsi(rsiPeriod);
	}

	@Override
	public void process(IDynami dynami, Event event) {
		// filter only on bar close events
		if(event.is(Event.Type.OnBarClose)){
			
			// collect close data on a Series
			close.append(event.bar.close);
			
			// compute technical indicators
			longTerm.compute(close);
			shortTerm.compute(close);
			rsi.compute(close);
			
			// start using technical indicators only if the longest can be properly computed, using isReady()
			if(longTerm.isReady()){
				// check trends
				boolean upwardTrend = longTerm.get().isGreaterThan(shortTerm.get());
				boolean downwardTrend = longTerm.get().isGreaterThan(shortTerm.get());
				
				// enter only if you are flat
				if(dynami.portfolio().isFlat(symbol)){
					// go long if you are in upward trend and if rsi is over sold
					if(upwardTrend && rsi.get().last() <= longThreshold){
						dynami.orders().send(new MarketOrder(symbol, 1, "go long"));
					}
					// go short if you are in downward trend and if rsi is over bought
					if(downwardTrend && rsi.get().last() >= shortThreshold){
						dynami.orders().send(new MarketOrder(symbol, -1, "go short"));
					}
				} else {
					// Plan exit on mean reverting strategy
					
					// exit long if price goes above short term moving average 
					if(dynami.portfolio().isLong(symbol) && close.crossesOver(shortTerm.get())){
						dynami.orders().send(new MarketOrder(symbol, -1, "exit long"));
					}
					
					// exit short if price goes below short term moving average
					if(dynami.portfolio().isShort(symbol) && close.crossesUnder(shortTerm.get())){
						dynami.orders().send(new MarketOrder(symbol, 1, "exit short"));
					}
				}
			}
		} 
	}
}
