package com.aregner.pandora;

import java.util.HashMap;
import java.util.Vector;


public class Station implements Comparable<Station> {
	private String id;
	private String idToken;
	private boolean isCreator;
	private boolean isQuickMix;
	private String name;

	private Song[] currentPlaylist;
	private boolean useQuickMix;
	private PandoraRadio pandora;

	public Station(HashMap<String, Object> d, PandoraRadio instance) {
		id = (String) d.get("stationId");
		idToken = (String) d.get("stationIdToken");
		isCreator = (Boolean) d.get("isCreator");
		isQuickMix = (Boolean) d.get("isQuickMix");
		name = (String) d.get("stationName");

		pandora = instance;
		useQuickMix = false;
	}
	
	public Song[] getPlaylist(boolean forceDownload) {
		return getPlaylist(PandoraRadio.DEFAULT_AUDIO_FORMAT, forceDownload);
	}
	
	public Song[] getPlaylist(String format, boolean forceDownload) {
		if(forceDownload || currentPlaylist == null) {
			return getPlaylist(format);
		}
		else {
			return currentPlaylist;
		}
	}

	@SuppressWarnings("unchecked")
	public Song[] getPlaylist(String format) {
		Vector<Object> args = new Vector<Object>(7);
		args.add(id);
		args.add("0");
		args.add("");
		args.add("");
		args.add(format);
		args.add("0");
		args.add("0");

		Object result = pandora.xmlrpcCall("playlist.getFragment", args);

		if(result instanceof Object[]) {
			Object[] fragmentsResult = (Object[]) result;
			Song[] list = new Song[fragmentsResult.length];
			for(int f=0; f<fragmentsResult.length; f++) {
				list[f] = new Song((HashMap<String,Object>)fragmentsResult[f], pandora);
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

	public String getStationImageUrl() {
		getPlaylist(false);
		return currentPlaylist[0].getAlbumCoverUrl();
	}
	
	public int compareTo(Station another) {
		return getName().compareTo(another.getName());
	}
	
	public boolean equals(Station another) {
		return getName().equals(another.getName());
	}

	public String getStationId() {
		return id;
	}

	public String getStationIdToken() {
		return idToken;
	}

	public boolean isCreator() {
		return isCreator;
	}

	public boolean isQuickMix() {
		return isQuickMix;
	}
}
