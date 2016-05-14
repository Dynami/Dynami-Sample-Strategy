/*
 * Copyright 2016 Alessandro Atria - a.atria@gmail.com
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
import org.dynami.core.Event.Type;
import org.dynami.core.IDynami;
import org.dynami.core.IStage;
import org.dynami.core.config.Config.Param;
import org.dynami.core.config.Config.Settings;
import org.dynami.core.data.Bar;
import org.dynami.core.data.Series;
import org.dynami.core.plot.Colors;
import org.dynami.core.plot.Plot;
import org.dynami.ta.momentum.Rsi;
/**
 * Personal version of warrior trading strategy from Warrior Trading 
 * {@link https://www.warriortrading.com/reversal-trading-strategy} 
 */
import org.dynami.ta.overlap_studies.Bbands;

import com.tictactec.ta.lib.MAType;
@Settings
public class WarriorReversal implements IStage {
	// WORK IN PROGRESS -
	// THIS IS NOT AN ADVICE FOR INVESTMENT.
	// PLEASE, READ DISCLAIMER IN DYNAMI PROJECT BLOG
	Rsi rsi;
	
	@Plot(color=Colors.BLUE)
	Bbands outerBB;
	
	@Plot(color=Colors.BLUEVIOLET)
	Bbands innerBB;
	
	@Param(name="RSI Period", description="Number of bars used to calculate the technical indicator")
	int rsiPeriod = 5;
	
	@Param(name="RSI Upper Threshold", description="Overbought threshold")
	double rsiUpperThreshold = 80;
	
	@Param(name="RSI Lower Threshold", description="Oversold threshold")
	double rsiLowerThreshold = 15;
	
	@Param(name="Outer Bollinger Bands Period")
	int outerBBPeriod = 20;
	
	@Param(name="Outer Bollinger Bands Std")
	double outerBBStd = 2.2;
	
	@Param(name="Inner Bollinger Bands Period")
	int innerBBPeriod = 20;
	
	@Param(name="Inner Bollinger Bands Std")
	double innerBBStd = 1.2;
	
	Series closes = new Series();
	Bar current, previous;
	
	double stop = Double.NaN;
	
	@Override
	public void setup(IDynami dynami) {
		rsi = new Rsi(rsiPeriod);
		outerBB = new Bbands(outerBBPeriod, outerBBStd-.2, outerBBStd, MAType.Sma);
		innerBB = new Bbands(innerBBPeriod, innerBBStd-.2, innerBBStd, MAType.Sma);
	}

	@Override
	public void process(IDynami dynami, Event event) {
		// Handle only on bar close events
		if(!event.is(Type.OnBarClose)) return;
		previous = current;
		current = event.bar;
		
		closes.append(current.close);
		
		rsi.compute(closes);
		outerBB.compute(closes);
		innerBB.compute(closes);
		// if all technical indicators are not ready skip execution
		if(!rsi.isReady() || !outerBB.isReady() || !innerBB.isReady()) return;
		
		if( dynami.portfolio().isFlat() // if is not on market
			&& !dynami.orders().thereArePendingOrders() // and ther are not pending orders
			&& closes.crossesUnder(outerBB.getRealUpperBand()) // and close price rises up over lower bollinger band
			&& rsi.get().last(1) >= rsiUpperThreshold
			){ // and rsi is oversold
			stop = Math.min(previous.high, current.high);
			dynami.orders().marketOrder(event.symbol, -1, "Go short");
		}
		// if is long and close price rises up over exit lower bb, or falls down entry lower bb
		if( dynami.portfolio().isShort(event.symbol)){
			stop = Math.min(stop, event.bar.high);
			if(!dynami.orders().thereArePendingOrders() 
				&& (closes.crossesOver(stop))){
				dynami.orders().marketOrder(event.symbol, 1, "Exit short");	
				stop = Double.NaN;
			}
		}
		
		if( dynami.portfolio().isFlat() // if is not on market
				&& !dynami.orders().thereArePendingOrders() // and ther are not pending orders
				&& closes.crossesOver(outerBB.getRealLowerBand()) // and close price rises up over lower bollinger band
				&& rsi.get().last(1) <= rsiLowerThreshold){ // and rsi is oversold
			stop = Math.min(previous.low, current.low);
			dynami.orders().marketOrder(event.symbol, 1, "Go long");
		}
		
		// if is long and close price rises up over exit lower bb, or falls down entry lower bb
		if( dynami.portfolio().isLong(event.symbol)){
			stop = Math.max(stop, event.bar.low);
			if(!dynami.orders().thereArePendingOrders() 
					&& closes.crossesUnder(stop)){
				dynami.orders().marketOrder(event.symbol, -1, "Exit long");	
				stop = Double.NaN;
			}
		}
	}
}
