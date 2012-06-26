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

import java.util.Map;

import android.util.Log;

public class Song {
	private String album;
	private String artist;
	//private String artistMusicId;
	//private String audioUrl;
	private String fileGain;
	//private String identity;
	private String musicId;
	private Integer rating;
	private String stationId;
	private String title;
	//private String userSeed;
	private String songDetailURL;
	private String albumDetailURL;
	private String album_art_url;
	//private Integer songType;
	private Map<String, String> audio_urls;

	private boolean tired;
	private String message;
	private Object startTime;
	private boolean finished;
	private long playlistTime;
	//private PandoraRadio pandora;

	public Song(Map<String,Object> d, Map<String, String> audio_urls_in) {
		try {
			//pandora = instance;
			
			album = (String) d.get("albumName");
			artist = (String) d.get("artistName");
			//artistMusicId = (String) d.get("artistMusicId");
			//audioUrl = (String) d.get("audioURL"); // needs to be hacked, see below
			fileGain = (String) d.get("trackGain");
			//identity = (String) d.get("identity");
			musicId = (String) d.get("trackToken");
			rating = (Integer) d.get("songRating");
			stationId = (String) d.get("stationId");
			title = (String) d.get("songName");
			//userSeed = (String) d.get("userSeed");
			songDetailURL = (String) d.get("songDetailURL");
			albumDetailURL = (String) d.get("albumDetailURL");
			album_art_url = (String) d.get("albumArtUrl");
			//songType = (Integer) d.get("songType");

			//int aul = audioUrl.length();
			//audioUrl = audioUrl.substring(0, aul-48) + pandora.pandoraDecrypt(audioUrl.substring(aul-48));

			audio_urls = audio_urls_in;
			
			//Our 192kbps audio is hiding!
			audio_urls.put(PandoraRadio.MP3_192, (String) ((Map<String,Object>) ((Map<String,Object>) d.get("audioUrlMap")).get("highQuality")).get("audioUrl"));
			
			tired = false;
			message = "";
			startTime = null;
			finished = false;
			playlistTime = System.currentTimeMillis() / 1000L;
		} catch(RuntimeException ex) {
			Log.e("Pandoroid","Runtime exception with song", ex);
			return;
		} //catch (BadPaddingException e) {
//			e.printStackTrace();
//		} catch (GeneralSecurityException e) {
//			e.printStackTrace();
//		}
	}

//	public int getSongType() {
//		return songType.intValue();
//	}

//	public String getUserSeed() {
//		return userSeed;
//	}

	public String getId() {
		return musicId;
	}

	public boolean isStillValid() {
		return ((System.currentTimeMillis() / 1000L) - playlistTime) < PandoraRadio.PLAYLIST_VALIDITY_TIME;
	}

	public String getAudioUrl(String audio_quality) {
		return audio_urls.get(audio_quality);
	}
	public String getAlbumCoverUrl() {
		return album_art_url;
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
	public Integer getRating() {
		return rating;
	}
//	public String getArtistMusicId() {
//		return artistMusicId;
//	}
	public String getFileGain() {
		return fileGain;
	}
//	public String getIdentity() {
//		return identity;
//	}
	public String getStationId() {
		return stationId;
	}
	public String getSongDetailURL() {
		return songDetailURL;
	}
	public String getAlbumDetailURL() {
		return albumDetailURL;
	}
	public boolean isTired() {
		return tired;
	}
	public String getMessage() {
		return message;
	}
	public Object getStartTime() {
		return startTime;
	}
	public boolean isFinished() {
		return finished;
	}
}
