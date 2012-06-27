package com.pandoroid.pandora;

public class RPCException extends Exception {
	public int code;
	RPCException(int error_code, String message) {
		super(message);
		code = error_code;
	}
}
