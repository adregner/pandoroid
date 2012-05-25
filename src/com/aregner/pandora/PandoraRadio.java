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
import java.util.ArrayList;
import java.util.Arrays;
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

import org.xmlrpc.android.XMLRPCException;
import org.json.*;

public class PandoraRadio {
	
	/*public static final String PROTOCOL_VERSION = "32";	
	private static final String RPC_URL = "http://www.pandora.com/radio/xmlrpc/v"+PROTOCOL_VERSION+"?";*/
	private static final String USER_AGENT = "com.aregner.pandora/0.1";
	
	//JSON API stuff
	public static final String PROTOCOL_VERSION = "5";
	private static final String RPC_URL = "tuner.pandora.com/services/json/";
	private static final String DEVICE_MODEL = "android-generic";
	private static final String PARTNER_USERNAME = "android";
	private static final String PARTNER_PASSWORD = "AC7IBG09A3DTSYM4R41UJWL07VLN8JI7";
	private static final String DECRYPT_CIPHER = "R=U!LH$O2B#";
	private static final String ENCRYPT_CIPHER = "6#26FRL$ZWD";
	private static final String MIME_TYPE = "text/plain";
	
	public static final long PLAYLIST_VALIDITY_TIME = 3600 * 3;
	public static final String DEFAULT_AUDIO_FORMAT = "aacplus";

	private static final Vector<Object> EMPTY_ARGS = new Vector<Object>();

	//private XmlRpc xmlrpc;
	private RPC pandora_rpc;
	private Cipher blowfish_encode;
	private Cipher blowfish_decode;
	private String authToken;
	//private String rid;
	private String webAuthToken;
	private ArrayList<Station> stations;

	public PandoraRadio() {
		
		pandora_rpc = new RPC(RPC_URL, MIME_TYPE, USER_AGENT);
		Map<String, String> partner_auth = new HashMap<String, String>(1);
		partner_auth.put("method", "auth.partnerLogin");
		String partner_return;
		try{
			partner_return = pandora_rpc.call(partner_auth, "{\"username\": \"" + PARTNER_USERNAME + "\",\"password\": \"" + PARTNER_PASSWORD + "\",\"deviceModel\": \"" + DEVICE_MODEL + "\",\"version\": \"" + PROTOCOL_VERSION + "\"}", true);
		}
		catch (Exception e){
			e.getMessage();
		}
		
		//xmlrpc = new XmlRpc(RPC_URL);
		//xmlrpc.addHeader("User-agent", USER_AGENT);
		
		try{
			//We're using the built in Blowfish cipher here. Not too sure if this works on Android 2.2 or lower
			blowfish_encode = Cipher.getInstance("Blowfish/ECB/NoPadding"); //new Blowfish(/*PandoraKeys.out_key_p, PandoraKeys.out_key_s*/);
			byte[] encrypt_key_data = ENCRYPT_CIPHER.getBytes();
			SecretKeySpec key_spec = new SecretKeySpec(encrypt_key_data, "Blowfish");
			blowfish_encode.init(Cipher.ENCRYPT_MODE, key_spec);
			
			blowfish_decode = Cipher.getInstance("Blowfish/ECB/NoPadding"); //new Blowfish(/*PandoraKeys.in_key_p, PandoraKeys.in_key_s*/);
			byte[] decrypt_key_data = DECRYPT_CIPHER.getBytes();
			key_spec = new SecretKeySpec(decrypt_key_data, "Blowfish");
			blowfish_decode.init(Cipher.DECRYPT_MODE, key_spec);
		}
		catch (NoSuchAlgorithmException e){
			
		}
		catch (NoSuchPaddingException e){
			
			
		}
		catch (InvalidKeyException e){
			
			
		}
		finally{
			//bugs happened
			
		}
	}

	private String pad(String s, int l) {
		String result = s;
		while(l - s.length() > 0) {
			result += '\0';
			l--;
		}
		return result;
	}

	private String fromHex(String hexText) {
		String decodedText=null;
		String chunk=null;
		if(hexText!=null && hexText.length()>0) {
			int numBytes = hexText.length()/2;
			char[] rawToByte = new char[numBytes];
			int offset=0;
			for(int i =0; i <numBytes; i++) {
				chunk = hexText.substring(offset,offset+2);
				offset+=2;
				rawToByte[i] = (char) (Integer.parseInt(chunk,16) & 0x000000FF);
			}
			decodedText= new String(rawToByte);
		}
		return decodedText;
	}

	public String pandoraEncrypt(String s) throws GeneralSecurityException, BadPaddingException {
		int length = s.length();
		StringBuilder result = new StringBuilder( length * 2 );
		int i8 = 0;
		for(int i=0; i<length; i+=8) {
			i8 = (i + 8 >= length) ? (length) : (i + 8);
			String substring = s.substring(i, i8);
			String padded = pad(substring, 8);
			final byte[] byte_string = blowfish_encode.doFinal(padded.getBytes());
			//long[] blownstring = blowfish_encode.doFinal(padded)//encrypt(padded.toCharArray());
//			for(int c=0; c<blownstring.length; c++) {
//				if(blownstring[c] < 0x10)
//					result.append("0");
//				result.append(Integer.toHexString((int)blownstring[c]));
//			}
			result.append(new String(byte_string));
		}
		return result.toString();
	}

	public String pandoraDecrypt(String s) throws GeneralSecurityException, BadPaddingException {
		StringBuilder result = new StringBuilder();
		int length = s.length();
		int i16 = 0;
		for(int i=0; i<length; i+=16) {
			i16 = (i + 16 > length) ? (length - 1) : (i + 16);
			//result.append( blowfish_decode.decrypt( pad( fromHex( s.substring(i, i16)), 8).toCharArray()));
			result.append(blowfish_decode.doFinal(s.substring(i, i16).getBytes()).toString());			
			
		}
		return result.toString().trim();
	}

	private String formatUrlArg(boolean v) {
		return v ? "true" : "false";
	}
	private String formatUrlArg(int v) {
		return String.valueOf(v);
	}
	private String formatUrlArg(long v) {
		return String.valueOf(v);
	}
	private String formatUrlArg(float v) {
		return String.valueOf(v);
	}
	private String formatUrlArg(double v) {
		return String.valueOf(v);
	}
	private String formatUrlArg(char v) {
		return String.valueOf(v);
	}
	private String formatUrlArg(short v) {
		return String.valueOf(v);
	}
	private String formatUrlArg(Object v) {
		return URLEncoder.encode(v.toString());
	}
	private String formatUrlArg(Object[] v) {
		StringBuilder result = new StringBuilder();
		for(int i=0; i<v.length; i++) {
			result.append(formatUrlArg(v[i]));
			if(i < v.length - 1)
				result.append("%2C");
		}
		return result.toString();
	}
	private String formatUrlArg(Iterator<?> v) {
		StringBuilder result = new StringBuilder();
		while(v.hasNext()) {
			result.append(formatUrlArg(v.next()));
			if(v.hasNext())
				result.append("%2C");
		}
		return result.toString();
	}
	private String formatUrlArg(Collection<?> v) {
		return formatUrlArg(v.iterator());
	}

	public Object doCall(String method, Vector<Object> args, Vector<Object> urlArgs){
		Object result = null;
		
		
		return result;
	}
	/*public static void printXmlRpc(String xml) {
		xml = xml.replace("<param>", "\n\t<param>").replace("</params>", "\n</params>");
		System.err.println(xml);
	}*/
	
	/*@SuppressWarnings("unchecked")
	private Object xmlrpcCall(String method, Vector<Object> args, Vector<Object> urlArgs) {
		if(urlArgs == null)
			urlArgs = (Vector<Object>) args.clone();

		args.add(0, new Long(System.currentTimeMillis()/1000L));
		if(authToken != null)
			args.add(1, authToken);

		String xml = XmlRpc.makeCall(method, args);
		//printXmlRpc(xml);
		String data = null;
		try{
			data = pandoraEncrypt(xml);
		}
		catch (BadPaddingException e){
			//something?		
		}
		catch (GeneralSecurityException e){
			
		}

		ArrayList<String> urlArgStrings = new ArrayList<String>();
		if(rid != null) {
			urlArgStrings.add("rid="+rid);
		}
		method = method.substring(method.lastIndexOf('.')+1);
		urlArgStrings.add("method="+method);
		Iterator<Object> urlArgsIter = urlArgs.iterator();
		int count = 1;
		while(urlArgsIter.hasNext()) {
			urlArgStrings.add("arg"+(count++)+"="+formatUrlArg(urlArgsIter.next()));
		}

		StringBuilder url = new StringBuilder(RPC_URL);
		Iterator<String> argIter = urlArgStrings.iterator();
		while(argIter.hasNext()) {
			url.append(argIter.next());
			if(argIter.hasNext())
				url.append("&");
		}

		Object result = null;
		try {
			result = xmlrpc.callWithBody(url.toString(), data);
		} catch (XMLRPCException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return result;
	}
	Object xmlrpcCall(String method, Vector<Object> args) {
		return xmlrpcCall(method, args, null);
	}
	private Object xmlrpcCall(String method) {
		return xmlrpcCall(method, EMPTY_ARGS, null);
	}*/

	@SuppressWarnings("unchecked")
	public void connect(String user, String password) {
		//rid = String.format("%07dP", System.currentTimeMillis() % 1000L);
		authToken = null;

		Vector<Object> args = new Vector<Object>(2);
		args.add(user); 
		args.add(password);

		Object result = doCall("listener.authenticateListener", args, EMPTY_ARGS);

		if(result instanceof HashMap<?,?>) {
			HashMap<String,Object> userInfo = (HashMap<String,Object>) result;

			webAuthToken = (String) userInfo.get("webAuthToken");
			authToken = (String) userInfo.get("authToken");
		}
	}
	
	public void disconnect() {
		authToken = null;
		webAuthToken = null;
		
		if(stations != null) {
			stations.clear();
			stations = null;
		}
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Station> getStations() {
		// get stations
		Object result = doCall("station.getStations", null, null);

		if(result instanceof Object[]) {
			Object[] stationsResult = (Object[]) result;
			stations = new ArrayList<Station>(stationsResult.length);
			for(int s=0; s<stationsResult.length; s++) {
				stations.add(new Station((HashMap<String,Object>)stationsResult[s], this));
			}
			Collections.sort(stations);
		}
		
		return stations;
	}
	
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

	public void rate(Station station, Song song, boolean rating) {
		Vector<Object> args = new Vector<Object>(7);
		args.add(String.valueOf(station.getId())); 
		args.add(song.getId()); 
		args.add(song.getUserSeed());
		args.add(""/*testStrategy*/); 
		args.add(rating); 
		args.add(false); 
		args.add(song.getSongType());
		
		doCall("station.addFeedback", args, null);
	}
	
	public void bookmarkSong(Station station, Song song) {
		Vector<Object> args = new Vector<Object>(2);
		args.add(String.valueOf(station.getId())); 
		args.add(song.getId());
		
		doCall("station.createBookmark", args, null);
	}
	
	public void bookmarkArtist(Station station, Song song) {
		Vector<Object> args = new Vector<Object>(1);
		args.add(song.getArtistMusicId());
		
		doCall("station.createArtistBookmark", args, null);
	}
	
	public void tired(Station station, Song song) {
		Vector<Object> args = new Vector<Object>(3);
		args.add(song.getId()); 
		args.add(song.getUserSeed()); 
		args.add(String.valueOf(station.getId()));
		
		doCall("listener.addTiredSong", args, null);
	}

	public boolean isAlive() {
		return authToken != null;
	}

	public class SearchResult {

	}


	public String test() throws Exception {
		// Right now this is just a little playing around with the idea of serializing Pandora data structures
		
		/*Console cons;
		char[] passwd;
		if ((cons = System.console()) != null && (passwd = cons.readPassword("[%s]", "Password:")) != null) {
			connect("andrew@aregner.com", new String(passwd));
			getStations();
			
			Station station = null;
			Iterator<Station> stationIter = stations.iterator();
			while(stationIter.hasNext()) {
				station = stationIter.next();
				if(station.getName().equals("0 - Pandoid Testing"))
					break;
			}
			System.out.println(station.getName());

			Song song = station.getPlaylist("mp3-hifi")[1];
			System.out.println(song.getTitle());
			
			boolean rating = true;

			//System.out.println("\nSerializing...");
			//(new ObjectOutputStream(System.out)).writeObject(station);
		}/**/
		
		return "test() method success";
	}

	public static void main(String[] args) throws Exception {
		PandoraRadio pandora = new PandoraRadio();
		System.out.println(pandora.test());
	}

}
