package com.aregner.pandora;

import java.util.HashMap;

public class SongResult extends SearchResult {
	private String sampleUrl;
	private String artistMusicId;
	private String identity;
	private String artistSummary;
	private String songTitle;
	private String sampleGain;
	private String songDetailUrl;
	
	public SongResult(HashMap<String, Object> d) {
		super(d);
		
		sampleUrl = (String) d.get("sampleUrl");
		artistMusicId = (String) d.get("artistMusicId");
		identity = (String) d.get("identity");
		artistSummary = (String) d.get("artistSummary");
		sampleGain = (String) d.get("sampleGain");
		songDetailUrl = (String) d.get("songDetailUrl");
		songTitle = (String) d.get("songTitle");
		
		super.artist = artistSummary;
		super.title = songTitle;
	}
	public String getArtistSummary(){
		return artist;
	}
	public String getSongTitle(){
		return songTitle;
	}
	
}