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
import org.dynami.core.plot.Plot;
import org.dynami.core.utils.CArray;
import org.dynami.ta.momentum.Rsi;
import org.dynami.ta.overlap_studies.MovingAverage;
import org.dynami.ta.stat.LinearRegAngle;
import org.dynami.ta.stat.LinearRegSlope;

import com.tictactec.ta.lib.MAType;

/**
 * Implementation in Dynami of RSI2 strategy developed by Larry Connors.
 * {@link http://stockcharts.com/school/doku.php?id=chart_school:trading_strategies:rsi2}
 */
@Config.Settings
public class Rsi2 implements IStage {
	// THIS IS NOT AN ADVICE FOR INVESTMENT.
	// PLEASE, READ DISCLAIMER IN DYNAMI PROJECT BLOG
	@Param(name="Trend sensitivity")
	double trendSensitivity = 60;

	@Param(description = "Number of contracts per transaction")
	int quantity = 1;
	
	@Param(description="Slow moving average period")
	int trendPeriod = 50;
	
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

	@Param(name="Threshold weight", description="Dynamic component for thresholds")
	double dynaThreshold = 30; // 5

	// Declare technical indicators and other stuffs
	LinearRegSlope trend;
	@Plot
	MovingAverage slowMA;
	
	MovingAverage fastMA;
	Rsi rsi;

	Series close = new Series();
	
	CArray stops = new CArray(3);
	CArray sensitivity = new CArray(trendPeriod);

	double exitLong, exitShort;

	@Override
	public void setup(IDynami dynami) {
		// It's recommended using setup method to initialize indicators, in order to setup parameters from external configuration file (future implementation)
		slowMA = new MovingAverage(slowPeriod, MAType.Sma);
		fastMA = new MovingAverage(fastPeriod, MAType.Ema);
		trend = new LinearRegSlope(trendPeriod);
		rsi = new Rsi(rsiPeriod);
	}


	@Override
	public void process(IDynami dynami, Event event) {
		// filter only on bar close events
		if(event.is(Event.Type.OnBarClose)){
			// collect close data on a Series
			close.append(event.bar.close);
			
			sensitivity.add(event.bar.close);

			// compute technical indicators
			slowMA.compute(close);
			fastMA.compute(close);
			trend.compute(close);
			rsi.compute(close);

			// start using technical indicators only if the longest can be properly computed, using isReady()
			if(trend.isReady()){
				// check trends
				//trendSensitivity = sensitivity._slope();

				dynami.trace().debug(getName(), String.format("Trend: %.2f ||| %.2f", trend.get().last(), trendSensitivity));
				boolean upwardTrend = close.isGreaterThan(slowMA.get());
				boolean downwardTrend = close.isLowerThan(slowMA.get());

				double _dynaThreshold = dynaThreshold * Math.abs(trend.get().last())/trendSensitivity;
				double _shortThreshold = shortThreshold - _dynaThreshold;
				double _longThreshold = longThreshold + _dynaThreshold;


				if(rsi.get().last() >= _shortThreshold || rsi.get().last() <= _longThreshold){
					dynami.trace().info(getName(), String.format("Potentially good entry point %.2f || %.2f | %.2f", rsi.get().last(), _shortThreshold, _longThreshold));
				}

				// enter only if you are flat
				if(dynami.portfolio().isFlat(SampleStrategy.symbol)){
					// go long if you are in upward trend and if rsi is over sold
					if(upwardTrend && rsi.get().last() <= _longThreshold /*&& close.isLowerThan(fastMA.get())*/){
						exitLong = stops.max();
						dynami.orders().marketOrder(SampleStrategy.symbol, quantity, "go long");
					}
					// go short if you are in downward trend and if rsi is over bought
					if(downwardTrend && rsi.get().last() >= _shortThreshold /*&& close.isGreaterThan(fastMA.get())*/){
						exitShort = stops.min();
						dynami.orders().marketOrder(SampleStrategy.symbol, -quantity, "go short");
					}
				} else {
					// Plan exit on mean reverting strategy
					exitLong = Math.max(exitLong, stops.max());
					exitShort = Math.min(exitShort, stops.min());

					// exit long if price goes above short term moving average
					if(dynami.portfolio().isLong(SampleStrategy.symbol) && close.last() < exitLong){
						dynami.orders().marketOrder(SampleStrategy.symbol, -quantity, "exit long");
						exitLong = 0;
					}

					// exit short if price goes below short term moving average
					if(dynami.portfolio().isShort(SampleStrategy.symbol) && close.last() > exitShort){
						dynami.orders().marketOrder(SampleStrategy.symbol, quantity, "exit short");
						exitShort = Double.MAX_VALUE;
					}
				}
			}
			stops.add(event.bar.close);
		}
	}
}
