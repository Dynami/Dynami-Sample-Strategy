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
 * limitations under the License.ch
 */
package org.sample.strategy;

import org.dynami.core.Event;
import org.dynami.core.Event.Type;
import org.dynami.core.IDynami;
import org.dynami.core.IStage;
import org.dynami.core.config.Config.Param;
import org.dynami.core.data.Series;
import org.dynami.ta.overlap_studies.Ema;
import org.dynami.ta.overlap_studies.Sma;
import org.dynami.ta.stat.StdDev;

public class Raptor implements IStage {
	private final int BARS_TO_COVER = 60;
	double lastHigh = 0, lastLow = Double.MAX_VALUE;
	double openDayPrice = 0;
	double previousHigh = 0, previousLow = 0, previousClose = 0, previousOpen = 0;
	int barCounter = 0;
	
	Sma longEma;
	Ema shortEma;
	StdDev stdDev;
	
	@Param(name="Long period ema")
	int longPeriod = 20;
	
	@Param(name="Short period ema")
	int shortPeriod = 5;
	
	double takeProfit, stopLoss;
	
	Series closes = new Series();
	@Override
	public void setup(IDynami dynami) {
		longEma = new Sma(longPeriod);
		shortEma = new Ema(shortPeriod);
		stdDev = new StdDev(longPeriod, 1);
	}

	@Override
	public void process(IDynami dynami, Event event) {
		
		if(event.is(Type.OnBarClose)){
			lastHigh = Math.max(lastHigh, event.bar.high);
			lastLow = Math.min(lastLow, event.bar.low);

			if(!dynami.portfolio().isFlat()) {
				if(takeProfit < event.bar.close) {
					dynami.orders().marketOrder(event.symbol, -1, "Take profit");
				}
				if(stopLoss > event.bar.close) {
					dynami.orders().marketOrder(event.symbol, -1, "Stop loss");
				}
			}
			
			barCounter++;
			if(barCounter >= BARS_TO_COVER) {
				dynami.orders().getPendingOrders().forEach(o -> dynami.orders().cancelOrder(o.id));
				// close all positions
				SampleStrategy.closeAll(dynami);				
			}
		}

		// reset all variables
		if(event.is(Type.OnDayClose)){
			closes.append(event.bar.close);
			
			longEma.compute(closes);
			shortEma.compute(closes);
			stdDev.compute(closes);
			//System.out.println("Computed series "+longEma.isReady());
			if(!longEma.isReady() || !shortEma.isReady()) return;
			// remove all pending orders
			dynami.orders().getPendingOrders().forEach(o -> dynami.orders().cancelOrder(o.id));
			// close all positions
			SampleStrategy.closeAll(dynami);

			// set previous data
			previousHigh = lastHigh;
			previousLow = lastLow;
			previousClose = event.bar.close;
			previousOpen = openDayPrice;	
			// reset daily settings
			lastHigh = 0;
			lastLow = Double.MAX_VALUE;
			barCounter = 0;
		}

		if(event.is(Type.OnDayOpen)){
			// do nothing if trend is not detectable
			if(!longEma.isReady() || !shortEma.isReady()) return;
			openDayPrice = event.bar.open;
			// upper trend
			if(shortEma.get().isGreaterThan(longEma.get()) 
					//&& stdDev.get().last() < (shortEma.get().last()-longEma.get().last()) 
					&& event.bar.close > shortEma.get().last()) {
				
				if(previousClose > event.bar.close) {
					dynami.orders().marketOrder(event.symbol, 1, "Go long on open");
					takeProfit = previousClose;
					stopLoss = event.bar.close-50;
				}
			}			
		}
	}
}
