package com.aregner.pandora;

import java.util.HashMap;

public class ArtistResult extends SearchResult {
	private int score;
	private String artistName;
	private boolean isComposer;
	private boolean likelyMatch;
	private String artistDetailUrl;

	public ArtistResult(HashMap<String, Object> d) {
		super(d);

		score = (Integer) d.get("score");
		artistName = (String) d.get("artistName");
		isComposer = (Boolean) d.get("isComposer");
		likelyMatch = (Boolean) d.get("likelyMatch");
		artistDetailUrl = (String) d.get("artistDetailUrl");
		
		artist = artistName;
	}
}