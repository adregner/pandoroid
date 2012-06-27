package com.pandoroid.pandora;

//A simple struct like class
public class PartnerCredentials {
	public String rpc_url;
	public String device_model;
	public String username;
	public String password;
	public String d_cipher;
	public String e_cipher;
	
	PartnerCredentials(){
		rpc_url = null;
		device_model = null;
		username = null;
		password = null;
		d_cipher = null;
		e_cipher = null;
	}
}
