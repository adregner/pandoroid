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


package com.pandoroid.pandora;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;


import org.json.JSONArray;
import org.json.JSONObject;

import android.util.Log;

/**
 * Description: Uses Pandora's JSON v5 API. Documentation of the JSON API
 * 	can be found here: http://pan-do-ra-api.wikia.com/wiki/Json/5 
 *  A network connection is required before any operation can take place.
 *  If a person is bored, and wants to work on something, they could split
 *  off the authentication component from this class while making both this
 *  and the authentication component inherit from the RPC class 
 *  (and also beef up the RPC class a bit). It would also be helpful
 *  to give this class a more meaningful name.
 */
public class PandoraRadio {
	private static final String USER_AGENT = "com.pandoroid.pandora/0.4";
	
	/*
	 * JSON API stuff
	 */
	public static final String PROTOCOL_VERSION = "5";
	
	//PandoraOne specific credentials
	private static final String ONE_RPC_URL = "internal-tuner.pandora.com/services/json/";
	private static final String ONE_DEVICE_ID = "D01";
	private static final String ONE_PARTNER_USERNAME = "pandora one";
	private static final String ONE_PARTNER_PASSWORD = "TVCKIBGS9AO9TSYLNNFUML0743LH82D";
	private static final String ONE_DECRYPT_CIPHER = "U#IO$RZPAB%VX2";
	private static final String ONE_ENCRYPT_CIPHER = "2%3WCL*JU$MP]4";
	
	//Android standard user specific credentials
	private static final String AND_RPC_URL = "tuner.pandora.com/services/json/";
	private static final String AND_DEVICE_ID = "android-generic";
	private static final String AND_PARTNER_USERNAME = "android";
	private static final String AND_PARTNER_PASSWORD = "AC7IBG09A3DTSYM4R41UJWL07VLN8JI7";
	private static final String AND_DECRYPT_CIPHER = "R=U!LH$O2B#";
	private static final String AND_ENCRYPT_CIPHER = "6#26FRL$ZWD";
	
	private static final String MIME_TYPE = "text/plain"; //This probably isn't important
	/* END API */
	
	public static final long PLAYLIST_VALIDITY_TIME = 3600 * 3;
	public static final String DEFAULT_AUDIO_FORMAT = "aacplus";
	public static final long MIN_TIME_BETWEEN_PLAYLIST_CALLS = 60; //seconds
	
	//Audio quality strings
	public static final String AAC_32 = "HTTP_32_AACPLUS";
	public static final String AAC_64 = "HTTP_64_AACPLUS";
	public static final String MP3_128 = "HTTP_128_MP3";
	public static final String MP3_192 = "HTTP_192_MP3";
	//End Audio

	private RPC pandora_rpc;
	private PartnerCredentials credentials;
	private Cipher blowfish_encode;
	private Cipher blowfish_decode;
	private String user_auth_token;
	private String partner_auth_token;
	private long sync_time;
	private long sync_obtained_time;
	private long last_acquired_playlist_time;
	private String last_acquired_playlist_station;
	private ArrayList<Station> stations;
	private Map<String, String> standard_url_params;

	/**
	 * 
	 */
	public PandoraRadio(){
		standard_url_params = new HashMap<String, String>();
		stations = new ArrayList<Station>();
		credentials = new PartnerCredentials();
		last_acquired_playlist_time = 0;
		last_acquired_playlist_station = new String();
			
	}
	
	public static int audioQualityCompare(String value, String relative_to) throws Exception{
		int str1_magnitude = getRelativeAudioQualityMagnitude(value);
		int str2_magnitude = getRelativeAudioQualityMagnitude(relative_to);
		if (str1_magnitude != -1 && str2_magnitude != -1){
			return (str1_magnitude - str2_magnitude);
		}		
		else{
			throw new Exception("Invalid strings to compare");
		}
	}
	
	/**
	 * Description: Disabled
	 */
	public void bookmarkArtist(Station station, Song song) {
		//Vector<Object> args = new Vector<Object>(1);
		//args.add(song.getArtistMusicId());
		
		//doCall("station.createArtistBookmark", args, null);
	}
	
	/**
	 * Description: Disabled
	 */
	public void bookmarkSong(Station station, Song song) {
//		Vector<Object> args = new Vector<Object>(2);
//		args.add(String.valueOf(station.getId())); 
//		args.add(song.getId());
		
		//doCall("station.createBookmark", args, null);
	}
	
	/**
	 * Description: Keeps track of the remote server's sync time.
	 */
	private int calcSync(){
		return (int) (sync_time 
				      + ((System.currentTimeMillis() / 1000L) 
				         - this.sync_obtained_time));
	}
	
	/**
	 * Description: Logs a user in.
	 * @param user
	 * @param password
	 * @return
	 * @throws Exception
	 * @throws RPCException
	 * @throws SubscriberTypeException
	 */
	public boolean connect(String user, String password) throws Exception, 
	                                                            RPCException, 
	                                                            SubscriberTypeException {
		if (!this.isPartnerAuthorized()){
			throw new Exception("Improper call to connect(), " +
					                      "the application is not authorized.");
		}
		
		Map<String, Object> request_args = new HashMap<String, Object>();
		request_args.put("loginType", "user");
		request_args.put("username", user);
		request_args.put("password", password);
		request_args.put("partnerAuthToken", this.partner_auth_token);
		
		JSONObject result = this.doCall("auth.userLogin", request_args, 
				                        true, true, null);
		
		//I've contemplated multiple ways of handling these.
		//If this is a PandoraOne subscriber and the credentials aren't correct
		if (!result.getBoolean("hasAudioAds") && !isPandoraOneCredentials()){
			throw new SubscriberTypeException(true, 
				"The subscriber is Pandora One and default device credentials were given.");
//			this.runPartnerLogin(true);
		}
		//If this is a non-PandoraOne subscriber and the credentials aren't correct
		else if (result.getBoolean("hasAudioAds") && isPandoraOneCredentials()){
			throw new SubscriberTypeException(false, 
				"The subscriber is standard and Pandora One device credentials were given.");
//			this.runPartnerLogin(false);
		}
		else{
			this.user_auth_token = result.getString("userAuthToken");
			this.standard_url_params.put("auth_token", user_auth_token);
			this.standard_url_params.put("user_id", result.getString("userId"));
			return user_auth_token != null;
		}
	}
	
	/**
	 * Effectively logs a user off.
	 */
	public void disconnect() {
		this.standard_url_params.remove("user_id");
		this.standard_url_params.put("auth_token", this.partner_auth_token);
		if(stations != null) {
			stations.clear();
			stations = null;
		}
	}
	
	/**
	 * Description: Here we are making our remote procedure call specifically
	 * 	using Pandora's JSON protocol. This will return a JSONObject holding
	 * 	the contents of the results key in the response. If an error occurs
	 * 	(ie "stat":"fail") an exception with the message body will be thrown.
	 * Caution: When debugging, be sure to note that most data that flows 
	 *  through here is time sensitive, and if stopped in the wrong places,
	 *  it will cause "stat":"fail" responses from the remote server.
	 */
	private JSONObject doCall(String method, 
			                  Map<String, Object> json_params,
						      boolean http_secure_flag, 
						      boolean encrypt,
						      Map<String, String> opt_url_params) throws Exception, 
						                                                 RPCException{
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
				throw new RPCException(response.getInt("code"),
									   response.getString("message"));
			}
			else{
				throw new Exception("RPC unknown error. stat: " + 
									response.getString("stat"));
			}
		}		
		
		return response.getJSONObject("result"); //Exception thrown if nonexistent
	}
	
	
	/**
	 * Description: Gets a list of songs to be played. This function should not
	 * 	be called more frequently than MIN_TIME_BETWEEN_PLAYLIST_CALLS allows
	 * 	or an error will result.
	 */
	@SuppressWarnings("unchecked")
	public Vector<Song> getPlaylist(String station_token) throws Exception{
		
		//This protects against a temporary account suspension from too many 
		//playlist requests.
		if (!isGetPlaylistCallValid(station_token)){
			throw new Exception("Playlist calls are too frequent");
		}
		
		if (!this.isUserAuthorized()){
			throw new Exception("Improper call to getPlaylist(), " +
					                    "the user has not been logged in yet.");
		}
		
		Vector<Song> songs = new Vector<Song>();
		
		try{
			Map<String, Object> request_args = new HashMap<String, Object>();
			request_args.put("stationToken", station_token);
			
			//Order matters in this URL request. The same order given here is 
			//the order received.
			request_args.put("additionalAudioUrl", 
					         MP3_128 + "," + AAC_32);
			
			JSONObject response = this.doCall("station.getPlaylist", request_args, 
					                          true, true, null);
			
			JSONArray songs_returned = response.getJSONArray("items");
			for (int i = 0; i < songs_returned.length(); ++i){
				Map<String, Object> song_data = JSONHelper.toMap(songs_returned.getJSONObject(i));				
				ArrayList<PandoraAudioUrl> audio_url_mappings = new ArrayList<PandoraAudioUrl>();
				if (song_data.get("additionalAudioUrl") instanceof Vector<?>){
					Vector<String> audio_urls = (Vector<String>) song_data.get("additionalAudioUrl");
//					for(String cur: audio_urls){
//						Log.v("Pandoroid","audio_urls: "+cur);
//					}
					//This has to be in the same order as the request.
					audio_url_mappings.add(new PandoraAudioUrl(MP3_128, 128, audio_urls.get(0)));
					audio_url_mappings.add(new PandoraAudioUrl(AAC_32, 32, audio_urls.get(1)));
				}
				//MP3_192 data
				if (isPandoraOneCredentials()){
					audio_url_mappings.add(new PandoraAudioUrl(
						(Map<String,Object>) (
							(Map<String,Object>) song_data.get("audioUrlMap")
							                 ).get("highQuality")
							                                  ));
				}
				//AAC_64 data
				audio_url_mappings.add(new PandoraAudioUrl(
					(Map<String,Object>) (
						(Map<String,Object>) song_data.get("audioUrlMap")
						                 ).get("mediumQuality")
						                                   ));			    
			    songs.add(new Song(song_data, audio_url_mappings));
			}
		}
		catch(RPCException e){
			if (RPCException.URL_PARAM_MISSING_METHOD <= e.code 
										&& 
				e.code <= RPCException.API_VERSION_NOT_SUPPORTED) {
				Log.e("Pandoroid","Exception getting playlist - throwing API change exception", e);
				throw new Exception("API Change");
			}
			else{ //It's probably something else.
				throw e;
			}
		}
		
		
		this.last_acquired_playlist_time = System.currentTimeMillis() / 1000L;
		this.last_acquired_playlist_station = station_token;
		
		return songs;
	}
	
	
	public static int getRelativeAudioQualityMagnitude(String quality_string){
		if (quality_string.compareTo(MP3_192) == 0){
			return 4;
		}
		if (quality_string.compareTo(MP3_128) == 0){
			return 3;
		}
		if (quality_string.compareTo(AAC_64) == 0){
			return 2;
		}
		if (quality_string.compareTo(AAC_32) == 0){
			return 1;
		}
		return -1;
	}
	
	/**
	 * Description: <to be filled>
	 */
	public Station getStationById(String sid) {
		Iterator<Station> stationIter = stations.iterator();
		Station station = null;
		while(stationIter.hasNext()) {
			station = stationIter.next();
			if(station.getStationId().compareTo(sid) == 0) {
				return station;
			}
		}
		return null;
	}
	
	
	/**
	 * Description: Retrieves the available stations, saves them to a 
	 * 	PandoraRadio member variable, and returns them.
	 */
	public ArrayList<Station> getStations() throws Exception {
		if (!this.isUserAuthorized()){
			throw new Exception("Improper call to getStations(), " +
					                    "the user has not been logged in yet.");
		}
		
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
	

	/**
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
	
	/**
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
		return isUserAuthorized();
	}
	
	private boolean isGetPlaylistCallValid(String station_token){
		if ((station_token.compareTo(last_acquired_playlist_station) == 0)
				                             &&
            (this.last_acquired_playlist_time < (		
        		  (System.currentTimeMillis() / 1000L) - MIN_TIME_BETWEEN_PLAYLIST_CALLS
		                                        )
		    )
		   ){
			return false;
			
		}
		return true;
	}
	
	public boolean isPandoraOneCredentials(){
		return (credentials.device_model == ONE_DEVICE_ID);
	}
	
	private boolean isPartnerAuthorized(){
		return (this.partner_auth_token != null);
	}
	
	private boolean isUserAuthorized(){
		return (this.user_auth_token != null);
	}
	

	
	/**
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
	
	
	/**
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


	/**
	 * Description: This is the authorization for the app itself.
	 */
	private void partnerLogin() throws Exception, RPCException{
		Map<String, Object> partner_params = new HashMap<String, Object>(4);
		partner_params.put("username", credentials.username);
		partner_params.put("password", credentials.password);
		partner_params.put("deviceModel", credentials.device_model);
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
	


	/**
	 * Description: Sends a song rating to the remote server.
	 */
	public void rate(Song song, boolean rating) throws Exception, RPCException{
		Map<String, Object> feedback_params = new HashMap<String, Object>(2);
		feedback_params.put("trackToken", song.getId());
		feedback_params.put("isPositive", rating);
		this.doCall("station.addFeedback", feedback_params, false, true, null);
	}
	
	/**
	 * Description: This will run a partner login with the proper user
	 * 	credentials as specified by the is_pandora_one variable.
	 */
	public void runPartnerLogin(boolean is_pandora_one) throws Exception, 
	                                                           RPCException{
		setCredentials(is_pandora_one);
		this.partnerLogin();
	}
	
	/**
	 * Description: Sets the cipher keys up.
	 * @throws Exception
	 */
	private void setCipher() throws NoSuchAlgorithmException, 
	                                NoSuchPaddingException,
	                                InvalidKeyException {
		//We're using the built in Blowfish cipher here.
		blowfish_encode = Cipher.getInstance("Blowfish/ECB/NoPadding");
		byte[] encrypt_key_data = this.credentials.e_cipher.getBytes();
		SecretKeySpec key_spec = new SecretKeySpec(encrypt_key_data, "Blowfish");
		blowfish_encode.init(Cipher.ENCRYPT_MODE, key_spec);
		
		blowfish_decode = Cipher.getInstance("Blowfish/ECB/NoPadding");
		byte[] decrypt_key_data = this.credentials.d_cipher.getBytes();
		key_spec = new SecretKeySpec(decrypt_key_data, "Blowfish");
		blowfish_decode.init(Cipher.DECRYPT_MODE, key_spec);
	}
	
	/**
	 * Description: This sets or resets (depending on how you look at it) the
	 * 	credentials for the app's authentication. 
	 * Postcondition: partnerLogin() will need to be called.
	 * @param is_pandora_one
	 * @throws Exception
	 */
	private void setCredentials(boolean is_pandora_one) throws Exception{
		if (is_pandora_one){
			credentials.rpc_url = ONE_RPC_URL;
			credentials.device_model = ONE_DEVICE_ID;
			credentials.username = ONE_PARTNER_USERNAME;
			credentials.password = ONE_PARTNER_PASSWORD;
			credentials.d_cipher = ONE_DECRYPT_CIPHER;
			credentials.e_cipher = ONE_ENCRYPT_CIPHER;
		}
		else{
			credentials.rpc_url = AND_RPC_URL;
			credentials.device_model = AND_DEVICE_ID;
			credentials.username = AND_PARTNER_USERNAME;
			credentials.password = AND_PARTNER_PASSWORD;
			credentials.d_cipher = AND_DECRYPT_CIPHER;
			credentials.e_cipher = AND_ENCRYPT_CIPHER;
		}		
		this.sync_time = 0;
		this.pandora_rpc = new RPC(this.credentials.rpc_url, MIME_TYPE, USER_AGENT);
		try {
			this.setCipher();
		}
		catch (Exception e){
			Log.e("Pandoroid", "Fatal error in cipher", e);
			throw new Exception("Cipher error", e);
		}
	}
	
	/**
	 * Description: The sync time from the remote server is rather special.
	 * 	It comes in hexadecimal form from which it must be dehexed to byte form,
	 * 	then it must be decrypted with the Blowfish decryption. From there,
	 *  it's hidden inside a string with 4 bytes of junk characters at the 
	 *  beginning, and two white space characters at the end.
	 *  Unfortunately Java is screwing with me in obtaining this time.
	 */
	private void setSync(String encoded_sync_time) throws Exception{
		this.sync_obtained_time = System.currentTimeMillis() / 1000L; 
		
//		//This time stamp contains a lot of junk in the string it's in.
//		String junk = pandoraDecrypt(encoded_sync_time); //First decrypt the hex
//		
//		//There is a problem with this algorithm and I suspect it's happening here
//		//in regards to the junk.substring() function. 
//		junk = junk.substring(4); //Remove the first 4 bytes of junk.
//		junk = junk.trim(); //Trim off the predictable white space chunks at the end.
//		this.sync_time = Long.parseLong(junk);
		
		//As long as our system clocks are accurate, using the system clock
		//is a suitable (and potentially long term) solution to this issue.
		this.sync_time = this.sync_obtained_time;
	}
	
	/**
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
}
