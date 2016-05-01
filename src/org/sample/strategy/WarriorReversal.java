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
	Bbands entryBB, exitBB;
	
	@Param(name="RSI Period", description="Number of bars used to calculate the technical indicator")
	int rsiPeriod = 5;
	
	@Param(name="RSI Upper Threshold", description="Overbought threshold")
	double rsiUpperThreshold = 50;
	
	@Param(name="RSI Lower Threshold", description="Oversold threshold")
	double rsiLowerThreshold = 50;
	
	@Param(name="Entry Bollinger Bands Period")
	int entryBBPeriod = 20;
	
	@Param(name="Entry Bollinger Bands Std")
	double entryBB1Std = 2.;
	
	@Param(name="Exit Bollinger Bands Period")
	int entryBB2Period = 20;
	
	@Param(name="Exit Bollinger Bands Std")
	double entryBB2Std = 0.8;
	
	Series closes = new Series();
	Bar current, previous;
	double stopLoss = Double.NaN;
	double[] pivots = new double[5];
	int pivotIdx = 0;
	
	@Override
	public void setup(IDynami dynami) {
		rsi = new Rsi(rsiPeriod);
		entryBB = new Bbands(entryBBPeriod, entryBB1Std, entryBB1Std, MAType.Sma);
		exitBB = new Bbands(entryBB2Period, entryBB2Std, entryBB2Std, MAType.Sma);
	}

	@Override
	public void process(IDynami dynami, Event event) {
		// Handle only on bar close events
		if(!event.is(Type.OnBarClose)) return;
		previous = current;
		current = event.bar;
		
		closes.append(current.close);
		
		rsi.compute(closes);
		entryBB.compute(closes);
		exitBB.compute(closes);
		// if all technical indicators are not ready skip execution
		if(!rsi.isReady() || !entryBB.isReady() || !exitBB.isReady()) return;
		
		// floating exit price depending on
		pivots[0] = entryBB.getRealUpperBand().last();
		pivots[1] = exitBB.getRealUpperBand().last();
		pivots[2] = entryBB.getRealMiddleBand().last();
		pivots[3] = exitBB.getRealLowerBand().last();
		pivots[4] = entryBB.getRealLowerBand().last();
		
//		if( dynami.portfolio().isFlat() // if is not on market
//			&& !dynami.orders().thereArePendingOrders() // and there are not pending orders
//			&& closes.crossesUnder(entryBB.getRealUpperBand().last()) // and close price falls down under upper bollinger band
//			&& rsi.get().last(1) >= rsiUpperThreshold){ // and previous rsi is overbought
//			stopLoss = Math.max(current.high, previous.high);
//			dynami.orders().marketOrder(event.symbol, -1, "Go short");
//		}
//		
//		// if is short and close price falls down below exit upper bb, or rises up entry upper bb
//		if( dynami.portfolio().isShort(event.symbol) &&
//			!dynami.orders().thereArePendingOrders() &&
//			( closes.crossesOver(stopLoss)
//			|| closes.crossesUnder(exitBB.getRealUpperBand().last()) 
//			|| closes.crossesOver(entryBB.getRealUpperBand().last()))){
//				
//			dynami.orders().marketOrder(event.symbol, 1, "Exit short");
//		}
		
		if( dynami.portfolio().isFlat() // if is not on market
			&& !dynami.orders().thereArePendingOrders() // and ther are not pending orders
			&& closes.crossesUnder(entryBB.getRealUpperBand().last()) // and close price rises up over lower bollinger band
			&& rsi.get().last(1) <= rsiUpperThreshold){ // and rsi is oversold
			stopLoss = Math.min(previous.high, current.high);
			pivotIdx = 0;
			dynami.orders().marketOrder(event.symbol, -1, "Go short");
		}
		// if is long and close price rises up over exit lower bb, or falls down entry lower bb
		if( dynami.portfolio().isShort(event.symbol)){
			if(closes.crossesUnder(pivots[Math.min(4,pivotIdx+1)])){
				pivotIdx++;
			}
			if(!dynami.orders().thereArePendingOrders() 
				&& ( closes.crossesOver(stopLoss) 
				|| closes.crossesOver(pivots[pivotIdx]))){
				dynami.orders().marketOrder(event.symbol, 1, "Exit short");	
			}
		}
		
		if( dynami.portfolio().isFlat() // if is not on market
				&& !dynami.orders().thereArePendingOrders() // and ther are not pending orders
				&& closes.crossesOver(entryBB.getRealLowerBand().last()) // and close price rises up over lower bollinger band
				&& rsi.get().last(1) <= rsiLowerThreshold){ // and rsi is oversold
			stopLoss = Math.min(previous.low, current.low);
			pivotIdx = 4;
			dynami.orders().marketOrder(event.symbol, 1, "Go long");
		}
		// if is long and close price rises up over exit lower bb, or falls down entry lower bb
		if( dynami.portfolio().isLong(event.symbol)){
			if(closes.crossesOver(pivots[Math.max(0,pivotIdx-1)])){
				pivotIdx--;
			}
			if(!dynami.orders().thereArePendingOrders() 
					&& ( closes.crossesUnder(stopLoss) || 
						closes.crossesUnder(pivots[pivotIdx]))){
				dynami.orders().marketOrder(event.symbol, -1, "Exit long");	
			}
		}
	}
}
