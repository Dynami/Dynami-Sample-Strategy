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
import org.dynami.core.config.Config;
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
@Config.Settings
public class Rsi2 implements IStage {
	// THIS IS NOT AN ADVICE FOR INVESTMENT.
	// PLEASE, READ DISCLAIMER IN DYNAMI PROJECT BLOG

	@Param(description="Slow moving average period")
	int slowPeriod = 21; //200
	@Param(description="Fast moving average period")
	int fastPeriod = 10; //5
	@Param(description="RSI period")
	int rsiPeriod = 2; // 2

	@Param(name="Overbought RSI threshold")
	double shortThreshold = 95; // 95
	@Param(name="Oversold RSI threshold")
	double longThreshold = 5; // 5

	// Declare technical indicators and other stuffs
	MovingAverage slowMA;
	MovingAverage fastMA;
	Rsi rsi;

	Series close = new Series();

	@Override
	public void setup(IDynami dynami) {
		// It's recommended using setup method to initialize indicators, in order to setup parameters from external configuration file (future implementation)
		slowMA = new MovingAverage(slowPeriod, MAType.Sma);
		fastMA = new MovingAverage(fastPeriod, MAType.Ema);
		rsi = new Rsi(rsiPeriod);
	}


	@Override
	public void process(IDynami dynami, Event event) {
		// filter only on bar close events
		if(event.is(Event.Type.OnBarClose)){
			// collect close data on a Series
			close.append(event.bar.close);

			// compute technical indicators
			slowMA.compute(close);
			fastMA.compute(close);
			rsi.compute(close);

			// start using technical indicators only if the longest can be properly computed, using isReady()
			if(slowMA.isReady()){
				// check trends

				boolean upwardTrend = close.isGreaterThan(slowMA.get());
				boolean downwardTrend = close.isLowerThan(slowMA.get());

				if(rsi.get().last() >= shortThreshold || rsi.get().last() <= longThreshold){
					dynami.trace().info(getName(), "Potentially good entry point "+rsi.get().last());
				}

				// enter only if you are flat
				if(dynami.portfolio().isFlat(SampleStrategy.symbol)){
					// go long if you are in upward trend and if rsi is over sold
					if(upwardTrend && rsi.get().last() <= longThreshold /*&& close.isLowerThan(fastMA.get())*/){
						dynami.orders().send(new MarketOrder(SampleStrategy.symbol, 1, "go long"));
					}
					// go short if you are in downward trend and if rsi is over bought
					if(downwardTrend && rsi.get().last() >= shortThreshold /*&& close.isGreaterThan(fastMA.get())*/){
						dynami.orders().send(new MarketOrder(SampleStrategy.symbol, -1, "go short"));
					}
				} else {
					// Plan exit on mean reverting strategy

					// exit long if price goes above short term moving average
					if(dynami.portfolio().isLong(SampleStrategy.symbol) && close.crossesOver(fastMA.get())){
						dynami.orders().send(new MarketOrder(SampleStrategy.symbol, -1, "exit long"));
					}

					// exit short if price goes below short term moving average
					if(dynami.portfolio().isShort(SampleStrategy.symbol) && close.crossesUnder(fastMA.get())){
						dynami.orders().send(new MarketOrder(SampleStrategy.symbol, 1, "exit short"));
					}
				}
			}
		}
	}
}
