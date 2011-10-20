package com.aregner.pandora;

import java.util.ArrayList;
import java.util.HashMap;

public class SearchResult {
	private String musicId;
	private String artUrl;
	protected String artist;
	protected String title;
	private boolean isComedy;
	
	public SearchResult(HashMap<String, Object> d) {
		artUrl = (String) d.get("artUrl");
		musicId = (String) d.get("musicId");
		isComedy = (Boolean) d.get("isComedy");		
	}
	
	public String getMusicId(){
		return musicId;
	}
	public String getArtist() {
		return artist;
	}

	public String getTitle() {
		return title;
	}
}
