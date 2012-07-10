package com.pandoroid.pandora;

public class SubscriberTypeException extends Exception {
	/**
	 * Eclipse auto-generated serialVersionUID
	 */
	private static final long serialVersionUID = -4684797455074629555L;
	
	public boolean is_pandora_one;
	SubscriberTypeException(boolean is_type_pandora_one, String message){
		super(message);
		is_pandora_one = is_type_pandora_one;
	}
}
