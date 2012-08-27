package com.pandoroid.playback;

import java.util.LinkedList;

public class TurboTimer {
	public static final int TURBO_TRIGGER_SIZE = 3;
	public static final int TURBO_TRIGGER_TIME_LENGTH = 5 * 60 * 1000; //5 minutes
	
	public boolean isTurbo(){
		cleanTimes();
		if (times.size() >= TURBO_TRIGGER_SIZE){
			return true;
		}
		return false;
	}
	
	public void updateForBuffer(){
		times.add((Long) System.currentTimeMillis());
	}
	
	private LinkedList<Long> times = new LinkedList<Long>();
	
	private void cleanTimes(){
		while(needsCleaned()){
			times.pop();
		}
	}
	
	private boolean needsCleaned(){
		Long time_stamp = times.peek();
		if (time_stamp != null){ 
			if (System.currentTimeMillis() - time_stamp > TURBO_TRIGGER_TIME_LENGTH){
				return true;
			}			
		}
		return false;
	}
}
