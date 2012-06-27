package com.pandoroid.pandora;

public class SubscriberTypeException extends Exception {
	public boolean is_pandora_one;
	SubscriberTypeException(boolean is_type_pandora_one, String message){
		super(message);
		is_pandora_one = is_type_pandora_one;
	}
}
