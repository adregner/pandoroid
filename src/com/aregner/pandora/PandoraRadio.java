package com.aregner.pandora;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import org.xmlrpc.android.XMLRPCException;


public class PandoraRadio {

	private static final String PROTOCOL_VERSION = "29";
	private static final String RPC_URL = "http://www.pandora.com/radio/xmlrpc/v"+PROTOCOL_VERSION+"?";
	private static final String USER_AGENT = "com.aregner.pandora/0.1";

	private static final long PLAYLIST_VALIDITY_TIME = 3600 * 3;

	private static final Vector<Object> EMPTY_ARGS = new Vector<Object>();

	private XmlRpc xmlrpc;
	private Blowfish blowfish_encode;
	private Blowfish blowfish_decode;
	private String authToken;
	private String rid;
	private String listenerId;
	private String webAuthToken;
	private ArrayList<Station> stations;

	public PandoraRadio() {
		xmlrpc = new XmlRpc(RPC_URL);
		xmlrpc.addHeader("User-agent", USER_AGENT);

		blowfish_encode = new Blowfish(PandoraKeys.out_key_p, PandoraKeys.out_key_s);
		blowfish_decode = new Blowfish(PandoraKeys.in_key_p, PandoraKeys.in_key_s);
	}

	private String pad(String s, int l) {
		String result = s;
		while(l - s.length() > 0) {
			result += '\0';
			l--;
		}
		return result;
	}

	/*private String toHex(String sourceText) {
		byte[] rawData = sourceText.getBytes();
		StringBuffer hexText= new StringBuffer();
		String initialHex = null;
		int initHexLength=0;
		for(int i=0; i<rawData.length; i++) {
			int positiveValue = rawData[i] & 0x000000FF;
			initialHex = Integer.toHexString(positiveValue);
			initHexLength=initialHex.length();
			while(initHexLength++ < 2) {
				hexText.append("0");
			}
			hexText.append(initialHex);
		}
		return hexText.toString();
	}*/

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

	private String pandoraEncrypt(String s) {
		int length = s.length();
		StringBuilder result = new StringBuilder( length * 2 );
		int i8 = 0;
		for(int i=0; i<length; i+=8) {
			i8 = (i + 8 >= length) ? (length) : (i + 8);
			String substring = s.substring(i, i8);
			String padded = pad(substring, 8);
			long[] blownstring = blowfish_encode.encrypt(padded.toCharArray());
			for(int c=0; c<blownstring.length; c++) {
				if(blownstring[c] < 0x10)
					result.append("0");
				result.append(Integer.toHexString((int)blownstring[c]));
			}
		}
		return result.toString();
	}

	private String pandoraDecrypt(String s) {
		StringBuilder result = new StringBuilder();
		int length = s.length();
		int i16 = 0;
		for(int i=0; i<length; i+=16) {
			i16 = (i + 16 > length) ? (length - 1) : (i + 16);
			result.append( blowfish_decode.decrypt( pad( fromHex( s.substring(i, i16)), 8).toCharArray()));
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

	@SuppressWarnings("unchecked")
	private Object xmlrpcCall(String method, Vector<Object> args, Vector<Object> urlArgs) {
		if(urlArgs == null)
			urlArgs = (Vector<Object>) args.clone();

		args.add(0, new Long(System.currentTimeMillis()/1000L));
		if(authToken != null)
			args.add(1, authToken);

		String xml = XmlRpc.makeCall(method, args);
		//System.err.println(xml);
		String data = pandoraEncrypt(xml);

		ArrayList<String> urlArgStrings = new ArrayList<String>();
		if(rid != null) {
			urlArgStrings.add("rid="+rid);
		}
		if(listenerId != null) {
			urlArgStrings.add("lid="+listenerId);
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
	private Object xmlrpcCall(String method, Vector<Object> args) {
		return xmlrpcCall(method, args, null);
	}
	private Object xmlrpcCall(String method) {
		return xmlrpcCall(method, EMPTY_ARGS, null);
	}

	@SuppressWarnings("unchecked")
	public void connect(String user, String password) {
		rid = String.format("%07dP", System.currentTimeMillis() % 1000L);
		listenerId = authToken = null;

		Vector<Object> args = new Vector<Object>(2);
		args.add(user); args.add(password);

		Object result = xmlrpcCall("listener.authenticateListener", args, EMPTY_ARGS);

		if(result instanceof HashMap<?,?>) {
			HashMap<String,Object> userInfo = (HashMap<String,Object>) result;

			webAuthToken = (String) userInfo.get("webAuthToken");
			listenerId = (String) userInfo.get("listenerId");
			authToken = (String) userInfo.get("authToken");
		}
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Station> getStations() {
		if(stations == null || stations.isEmpty()) {
			// get stations
			Object result = xmlrpcCall("station.getStations");

			if(result instanceof Object[]) {
				Object[] stationsResult = (Object[]) result;
				stations = new ArrayList<Station>(stationsResult.length);
				for(int s=0; s<stationsResult.length; s++) {
					stations.add(new Station((HashMap<String,Object>)stationsResult[s]));
				}
				Collections.sort(stations);
			}
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

	public boolean isAlive() {
		return authToken != null;
	}

	public class Station implements Comparable<Station> {
		private String id;
		private String idToken;
		private boolean isCreator;
		private boolean isQuickMix;
		private String name;

		private Song[] currentPlaylist;
		private boolean useQuickMix;

		public Station(HashMap<String, Object> d) {
			id = (String) d.get("stationId");
			idToken = (String) d.get("stationIdToken");
			isCreator = (Boolean) d.get("isCreator");
			isQuickMix = (Boolean) d.get("isQuickMix");
			name = (String) d.get("stationName");

			useQuickMix = false;
		}
		
		public Song[] getPlaylist(boolean forceDownload) {
			if(forceDownload || currentPlaylist == null) {
				return getPlaylist();
			}
			else {
				return currentPlaylist;
			}
		}

		@SuppressWarnings("unchecked")
		public Song[] getPlaylist() {
			Vector<Object> args = new Vector<Object>(7);
			args.add(id);
			args.add("0");
			args.add("");
			args.add("");
			args.add("aacplus");
			args.add("0");
			args.add("0");

			Object result = xmlrpcCall("playlist.getFragment", args);

			if(result instanceof Object[]) {
				Object[] fragmentsResult = (Object[]) result;
				Song[] list = new Song[fragmentsResult.length];
				for(int f=0; f<fragmentsResult.length; f++) {
					list[f] = new Song((HashMap<String,Object>)fragmentsResult[f]);
				}
				currentPlaylist = list;
			}

			return currentPlaylist;
		}

		public long getId() {
			try {
				return Long.parseLong(id);
			} catch(NumberFormatException ex) {
				return id.hashCode();
			}
		}

		public String getName() {
			return name;
		}

		public String getAlbumCoverUrl() {
			getPlaylist(false);
			return currentPlaylist[0].getAlbumCoverUrl();
		}

		public int compareTo(Station another) {
			return getName().compareTo(another.getName());
		}
		
		public boolean equals(Station another) {
			return getName().equals(another.getName());
		}
	}

	public class Song {
		private String album;
		private String artist;
		private String artistMusicId;
		private String audioUrl;
		private String fileGain;
		private String identity;
		private String musicId;
		private Integer rating;
		private String stationId;
		private String title;
		private String userSeed;
		private String songDetailURL;
		private String albumDetailURL;
		private String artRadio;
		private Integer songType;

		private boolean tired;
		private String message;
		private Object startTime;
		private boolean finished;
		private long playlistTime;

		public Song(HashMap<String,Object> d) {
			try {
				album = (String) d.get("albumTitle");
				artist = (String) d.get("artistSummary");
				artistMusicId = (String) d.get("artistMusicId");
				audioUrl = (String) d.get("audioURL"); // needs to be hacked, see below
				fileGain = (String) d.get("fileGain");
				identity = (String) d.get("identity");
				musicId = (String) d.get("musicId");
				rating = (Integer) d.get("rating");
				stationId = (String) d.get("stationId");
				title = (String) d.get("songTitle");
				userSeed = (String) d.get("userSeed");
				songDetailURL = (String) d.get("songDetailURL");
				albumDetailURL = (String) d.get("albumDetailURL");
				artRadio = (String) d.get("artRadio");
				songType = (Integer) d.get("songType");

				int aul = audioUrl.length();
				audioUrl = audioUrl.substring(0, aul-48) + pandoraDecrypt(audioUrl.substring(aul-48));

				tired = false;
				message = "";
				startTime = null;
				finished = false;
				playlistTime = System.currentTimeMillis() / 1000L;
			} catch(RuntimeException ex) {
				ex.printStackTrace();
				return;
			}
		}

		public boolean isStillValid() {
			return ((System.currentTimeMillis() / 1000L) - playlistTime) < PLAYLIST_VALIDITY_TIME;
		}

		public String getAudioUrl() {
			return audioUrl;
		}
		public String getAlbumCoverUrl() {
			// TODO Auto-generated method stub
			return null;
		}

		public String getTitle() {
			return title;
		}

		public String getArtist() {
			return artist;
		}

		public String getAlbum() {
			return album;
		}
	}

	public class SearchResult {

	}


	public String test() {
		return null;
	}

	public static void main(String[] args) {
		PandoraRadio pandora = new PandoraRadio();
		System.out.println(pandora.test());
	}

}
