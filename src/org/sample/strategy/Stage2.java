package org.sample.strategy;

import org.dynami.core.Event;
import org.dynami.core.IDynami;
import org.dynami.core.IStage;
import org.dynami.core.Event.Type;

public class Stage2 implements IStage {

	@Override
	public void setup(IDynami dynami) {
		// TODO Auto-generated method stub

	}

	@Override
	public void process(IDynami dynami, Event event) {
		if(event.bar == null) return;
		
		System.out.print("Stage2.process() "+event.bar);
		if(event.is(Type.OnBarClose)){
			System.out.print(" CLOSE");
		}
		if(event.is(Type.OnBarOpen)){
			System.out.print(" OPEN");
		}
		if(event.is(Type.OnDayOpen)){
			System.out.print(" DAY_OPEN");
		}
		if(event.is(Type.OnDayClose)){
			System.out.print(" DAY_CLOSE");
		}
		System.out.println();
	}

}
