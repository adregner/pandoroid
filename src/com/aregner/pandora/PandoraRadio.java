/* Pandoroid Radio - open source pandora.com client for android
 * Copyright (C) 2011  Andrew Regner <andrew@aregner.com>
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/* This class is designed to be used as a stand-alone Java module for interacting
 * with Pandora Radio.  Other then the XmlRpc class which is based on the android
 * library, this class should run in any Java VM.
 */
package com.aregner.pandora;

//import java.io.Console; //Not supported by android's JVM - used for testing this class with java6 on PC/Mac

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


import org.json.JSONArray;
import org.json.JSONObject;

/*
 * Description: Uses Pandora's JSON v5 API. Documentation of the JSON API
 * 	can be found here: http://pan-do-ra-api.wikia.com/wiki/Json/5 
 */
public class PandoraRadio {
	private static final String USER_AGENT = "com.aregner.pandora/0.1";
	
	//JSON API stuff
	public static final String PROTOCOL_VERSION = "5";
	private static final String RPC_URL = "tuner.pandora.com/services/json/";
	private static final String DEVICE_MODEL = "android-generic";
	private static final String PARTNER_USERNAME = "android";
	private static final String PARTNER_PASSWORD = "AC7IBG09A3DTSYM4R41UJWL07VLN8JI7";
	private static final String DECRYPT_CIPHER = "R=U!LH$O2B#";
	private static final String ENCRYPT_CIPHER = "6#26FRL$ZWD";
	private static final String MIME_TYPE = "text/plain"; //This probably isn't important
	//END API
	
	public static final long PLAYLIST_VALIDITY_TIME = 3600 * 3;
	public static final String DEFAULT_AUDIO_FORMAT = "aacplus";
	
	//Audio quality strings
	public static String AAC_64 = "HTTP_64_AACPLUS_ADTS";
	public static String MP3_128 = "HTTP_128_MP3";
	public static String MP3_192 = "HTTP_192_MP3";
	//End Audio

	private RPC pandora_rpc;
	private Cipher blowfish_encode;
	private Cipher blowfish_decode;
	private String user_auth_token;
	private String partner_auth_token;
	private long sync_time;
	private long sync_obtained_time;
	private ArrayList<Station> stations;
	private Map<String, String> standard_url_params;

	
	public PandoraRadio() {
		
		pandora_rpc = new RPC(RPC_URL, MIME_TYPE, USER_AGENT);
		standard_url_params = new HashMap<String, String>();
		stations = new ArrayList<Station>();
		
		try{
			//We're using the built in Blowfish cipher here. Not too sure if 
			//this works on Android 2.2 or lower
			blowfish_encode = Cipher.getInstance("Blowfish/ECB/NoPadding"); //new Blowfish(/*PandoraKeys.out_key_p, PandoraKeys.out_key_s*/);
			byte[] encrypt_key_data = ENCRYPT_CIPHER.getBytes();
			SecretKeySpec key_spec = new SecretKeySpec(encrypt_key_data, "Blowfish");
			blowfish_encode.init(Cipher.ENCRYPT_MODE, key_spec);
			
			blowfish_decode = Cipher.getInstance("Blowfish/ECB/NoPadding"); //new Blowfish(/*PandoraKeys.in_key_p, PandoraKeys.in_key_s*/);
			byte[] decrypt_key_data = DECRYPT_CIPHER.getBytes();
			key_spec = new SecretKeySpec(decrypt_key_data, "Blowfish");
			blowfish_decode.init(Cipher.DECRYPT_MODE, key_spec);
			
			this.partnerLogin();
		}
		catch (Exception e){
			e.getMessage();
		}		
	}
	
	/*
	 * Description: Disabled
	 */
	public void bookmarkArtist(Station station, Song song) {
		//Vector<Object> args = new Vector<Object>(1);
		//args.add(song.getArtistMusicId());
		
		//doCall("station.createArtistBookmark", args, null);
	}
	
	/*
	 * Description: Disabled
	 */
	public void bookmarkSong(Station station, Song song) {
//		Vector<Object> args = new Vector<Object>(2);
//		args.add(String.valueOf(station.getId())); 
//		args.add(song.getId());
		
		//doCall("station.createBookmark", args, null);
	}
	
	/*
	 * Description: Keeps track of the remote server's sync time.
	 */
	private int calcSync(){
		return (int) (sync_time 
				      + ((System.currentTimeMillis() / 1000L) 
				         - this.sync_obtained_time));
	}
	
	/*
	 * Description: Logs a user in.
	 */
	public void connect(String user, String password) throws Exception {
		this.partnerLogin();
		
		Map<String, Object> request_args = new HashMap<String, Object>();
		request_args.put("loginType", "user");
		request_args.put("username", user);
		request_args.put("password", password);
		request_args.put("partnerAuthToken", this.partner_auth_token);
		
		//One extra possible request token.
		//request_args.put("includeDemographics", true); 
		
		JSONObject result = this.doCall("auth.userLogin", request_args, 
				                        true, true, null);

		this.user_auth_token = result.getString("userAuthToken");
		this.standard_url_params.put("auth_token", user_auth_token);
		this.standard_url_params.put("user_id", result.getString("userId"));
	}
	
	/*
	 * Description: Effectively logs a user off.
	 */
	public void disconnect() {
		this.standard_url_params.remove("user_id");
		this.standard_url_params.put("auth_token", this.partner_auth_token);
		if(stations != null) {
			stations.clear();
			stations = null;
		}
	}
	
	/*
	 * Description: Here we are making our remote procedure call specifically
	 * 	using Pandora's JSON protocol. This will return a JSONObject holding
	 * 	the contents of the results key in the response. If an error occurs
	 * 	(ie "stat":"fail") an exception with the message body will be thrown.
	 * Caution: When debugging, be sure to note that most data that flows 
	 *  through here is time sensitive, and if stopped in the wrong places
	 *  will cause "stat":"fail" responses from the remote server.
	 */
	private JSONObject doCall(String method, Map<String, Object> json_params,
						      boolean http_secure_flag, boolean encrypt,
						      Map<String, String> opt_url_params) throws Exception{
		JSONObject response = null;
		JSONObject request = null;
		if (json_params != null){
			request = new JSONObject(json_params);
		}
		else{
			request = new JSONObject();
		}
		
		Map<String, String> 
			url_params = new HashMap<String, String>(standard_url_params);
		url_params.put("method", method);
		if (opt_url_params != null){
			url_params.putAll(opt_url_params);
		}
		
		if (user_auth_token != null){
			request.put("userAuthToken", user_auth_token);
		}
		if (sync_time != 0){
			request.put("syncTime", calcSync());			
		}
		
		String request_string = request.toString();
		if (encrypt){
			request_string = this.pandoraEncrypt(request_string);
		}
		
		
		String response_string = pandora_rpc.call(url_params, 
												  request_string, 
												  http_secure_flag);
		response = new JSONObject(response_string);
		if (response.getString("stat").compareTo("ok") != 0){
			if (response.getString("stat").compareTo("fail") == 0){
				throw new Exception("RPC failure code: " +
									response.getString("code") +
									"; message: " +
									response.getString("message"));
			}
			else{
				throw new Exception("RPC unknown error. stat: " + 
									response.getString("stat"));
			}
		}		
		
		return response.getJSONObject("result"); //Exception thrown if nonexistent
	}
	
	/*
	 * Description: Gets a list of songs to be played.
	 */
	public Vector<Song> getPlaylist(String station_token) throws Exception{
		Vector<Song> songs = new Vector<Song>();
		
		try{
			Map<String, Object> request_args = new HashMap<String, Object>();
			request_args.put("stationToken", station_token);
			
			//Order matters in this URL request. The same order given here is 
			//the order received.
			request_args.put("additionalAudioUrl", 
					         AAC_64 + "," + MP3_128 + "," + MP3_192);
			
			JSONObject response = this.doCall("station.getPlaylist", request_args, 
					                          true, true, null);
			
			JSONArray songs_returned = response.getJSONArray("items");
			for (int i = 0; i < songs_returned.length(); ++i){
				Map<String, Object> song_data = JSONHelper.toMap(songs_returned.getJSONObject(i));
				Map<String, String> audio_url_mappings = new HashMap<String, String>();
				if (song_data.get("additionalAudioUrl") instanceof Vector<?>){
					Vector<String> audio_urls = (Vector<String>) song_data.get("additionalAudioUrl");
					
					//This has to be in the same order as the request.
					audio_url_mappings.put(AAC_64, audio_urls.get(0));
					audio_url_mappings.put(MP3_128, audio_urls.get(1));
					audio_url_mappings.put(MP3_192, audio_urls.get(2));
				}
				songs.add(new Song(song_data, audio_url_mappings));				
			}
		}
		catch(Exception e){
			throw new Exception("API Change");
		}
		
		return songs;
	}
	
	/*
	 * Description: <to be filled>
	 */
	public Station getStationById(long sid) {
		Iterator<Station> stationIter = stations.iterator();
		Station station = null;
		while(stationIter.hasNext()) {
			station = stationIter.next();
			if(station.getId() == sid) {
				return station;
			}
		}
		return null;
	}
	
	
	/*
	 * Description: Retrieves the available stations, saves them to a 
	 * 	PandoraRadio member variable, and returns them.
	 */
	public ArrayList<Station> getStations() throws Exception {
		// get stations
		
		JSONObject result = doCall("user.getStationList", null, 
				                   false, true, null);
		
		//Our stations come in a JSONArray within the JSONObject
		JSONArray result_stations = result.getJSONArray("stations");

		//Run through the stations within the array, and pick out some of the
		//properties we want.
		for (int i = 0; i < result_stations.length(); ++i){
			JSONObject single_station = result_stations.getJSONObject(i);
			HashMap<String, Object> station_prep = new HashMap<String, Object>();
			station_prep.put("stationId", single_station.get("stationId"));
			station_prep.put("stationIdToken", single_station.get("stationToken"));
			station_prep.put("stationName", single_station.get("stationName"));
			station_prep.put("isQuickMix", single_station.get("isQuickMix"));
			stations.add(new Station(station_prep, this));
		}
		
		return stations;
	}
	

	/*
	 * Description: Self explanatory function that converts from a hex string
	 * 	to a plain string. One complicated portion of this to mention is that
	 *  String types can do something rather odd when conversions are made
	 *  from byte arrays to Strings and back again. They don't like to work 
	 *  out perfectly.
	 */
	private byte[] fromHex(String hex_text) {
		int hex_len = hex_text.length();
		byte[] raw = new byte[hex_len / 2];
		for (int i = 0; i < hex_len; i += 2){
			raw[i / 2] = (byte) ((Character.digit(hex_text.charAt(i), 16) * 16)
					             + Character.digit(hex_text.charAt(i + 1), 16));
		}
		return raw;
	}
	
	/*
	 * Description: I had to look far and wide to find this implementation.
	 * 	Java's builtin Integer.toHexString() function is absolutely atrocious.
	 *  A person can't depend on it for any kind of formatting predictability.
	 *  For speed's sake, having the HEX_CHARS constant is a necessity.
	 */
	private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
	private String toHex(byte[] clean_text){
		char[] chars = new char[2 * clean_text.length];
        for (int i = 0; i < clean_text.length; ++i){
            chars[2 * i] = HEX_CHARS[(clean_text[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[clean_text[i] & 0x0F];
        }
		
		return new String(chars);
	}

	
	public boolean isAlive() {
		return user_auth_token != null;
	}
	

	
	/*
	 * Description: This takes a Blowfish encrypted, hexadecimal string, and 
	 *  decrypts it to a plain string form.
	 */
	public String pandoraDecrypt(String s) 
			throws GeneralSecurityException, BadPaddingException {
		byte[] bytes = fromHex(s);
		byte[] raw_decoded = blowfish_decode.doFinal(bytes);
		String result_string = new String(raw_decoded);
		return result_string;
	}
	
	
	/*
	 * Description: This function encrypts a string using a Blowfish cipher, 
	 * 	and returns	the hexadecimal representation of the encryption.
	 */
	public String pandoraEncrypt(String s) 
			throws GeneralSecurityException, BadPaddingException {
		byte[] byte_s = s.getBytes();
		
		if (byte_s.length % 8 != 0){			
			int padding_size = 8 - (byte_s.length % 8);
			byte[] tmp_bytes = new byte[byte_s.length + padding_size];
			System.arraycopy(byte_s, 0, tmp_bytes, 0, byte_s.length);
			byte_s = new byte[tmp_bytes.length];
			System.arraycopy(tmp_bytes, 0, byte_s, 0, tmp_bytes.length);
		}
		byte[] encode_raw = blowfish_encode.doFinal(byte_s);
		String result_string = new String(encode_raw);
		result_string = toHex(encode_raw);
		return result_string;
	}


	/*
	 * Description: This is the authorization for the app itself.
	 */
	private void partnerLogin() throws Exception{
		Map<String, Object> partner_params = new HashMap<String, Object>(4);
		partner_params.put("username", PARTNER_USERNAME);
		partner_params.put("password", PARTNER_PASSWORD);
		partner_params.put("deviceModel", DEVICE_MODEL);
		partner_params.put("version", PROTOCOL_VERSION);
		JSONObject partner_return = null;
		
		partner_return = this.doCall("auth.partnerLogin", partner_params, 
                				     true, false, null);
		partner_auth_token = partner_return.getString("partnerAuthToken");
		standard_url_params.put("partner_id", 
							    partner_return.getString("partnerId"));
		setSync(partner_return.getString("syncTime"));
		standard_url_params.put("auth_token", partner_auth_token);
	}

	/*
	 * Description: Disabled
	 */
	public void rate(Station station, Song song, boolean rating) {
//		Vector<Object> args = new Vector<Object>(7);
//		args.add(String.valueOf(station.getId())); 
//		args.add(song.getId()); 
//		//args.add(song.getUserSeed());
//		args.add(""/*testStrategy*/); 
//		args.add(rating); 
//		args.add(false); 
		//args.add(song.getSongType());
		
		//doCall("station.addFeedback", args, null);
	}
	
	/*
	 * Description: The sync time from the remote server is rather special.
	 * 	It comes in hexadecimal form from which it must be dehexed to byte form,
	 * 	then it must be decrypted with the Blowfish decryption. From there,
	 *  it's hidden inside a string with 4 bytes of junk characters at the 
	 *  beginning, and two white space characters at the end.
	 */
	private void setSync(String encoded_sync_time) throws Exception{		
		//This time stamp contains a lot of junk in the string it's in.
		String junk = pandoraDecrypt(encoded_sync_time); //First decrypt the hex
		junk = junk.substring(4); //Remove the first 4 bytes of junk.
		junk = junk.trim(); //Trim off the predictable white space chunks at the end.
		long sync_time_obtained = Long.parseLong(junk);
		
		this.sync_time = sync_time_obtained;
		this.sync_obtained_time = System.currentTimeMillis() / 1000L; 
	}
	
	/*
	 * Description: Disabled
	 */
	public void tired(Station station, Song song) {
//		Vector<Object> args = new Vector<Object>(3);
//		args.add(song.getId()); 
//		//args.add(song.getUserSeed()); 
//		args.add(String.valueOf(station.getId()));
//		
//		//doCall("listener.addTiredSong", args, null);
	}


//	public String test() throws Exception {
//		// Right now this is just a little playing around with the idea of serializing Pandora data structures
//		
//		/*Console cons;
//		char[] passwd;
//		if ((cons = System.console()) != null && (passwd = cons.readPassword("[%s]", "Password:")) != null) {
//			connect("andrew@aregner.com", new String(passwd));
//			getStations();
//			
//			Station station = null;
//			Iterator<Station> stationIter = stations.iterator();
//			while(stationIter.hasNext()) {
//				station = stationIter.next();
//				if(station.getName().equals("0 - Pandoid Testing"))
//					break;
//			}
//			System.out.println(station.getName());
//
//			Song song = station.getPlaylist("mp3-hifi")[1];
//			System.out.println(song.getTitle());
//			
//			boolean rating = true;
//
//			//System.out.println("\nSerializing...");
//			//(new ObjectOutputStream(System.out)).writeObject(station);
//		}/**/
//		
//		return "test() method success";
//	}
//
//	public static void main(String[] args) throws Exception {
//		PandoraRadio pandora = new PandoraRadio();
//		System.out.println(pandora.test());
//	}
	

	

	

	


}
