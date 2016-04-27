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

import org.dynami.core.IDynami;
import org.dynami.core.IStage;
import org.dynami.core.IStrategy;
import org.dynami.core.config.Config;

@Config.Settings
public class SampleStrategy implements IStrategy {

	@Config.Param(name="Symbol", description="Asset used in the strategy")
	static String symbol = "FTSEMIB";

	@Override
	public IStage startsWith() {
		return new Rsi2();
	}

	public static void closeAll(IDynami dynami){
		dynami.portfolio().getOpenPositions().forEach(op->{
			dynami.orders().marketOrder(op.asset.symbol, -op.quantity, "close all");
		});

		dynami.orders().removePendings();
	}
}
