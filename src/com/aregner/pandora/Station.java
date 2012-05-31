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
package com.aregner.pandora;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Vector;


public class Station implements Comparable<Station>, Serializable {
	private static final long serialVersionUID = 1L;
	
	private String id;
	private String idToken;
	//private boolean isCreator;
	private boolean isQuickMix;
	private String name;

	transient private Song[] currentPlaylist;
	transient private boolean useQuickMix;
	transient private PandoraRadio pandora;

	public Station(HashMap<String, Object> d, PandoraRadio instance) {
		id = (String) d.get("stationId");
		idToken = (String) d.get("stationIdToken");
		//isCreator = (Boolean) d.get("isCreator");
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
			return getPlaylist();
		}
		else {
			return currentPlaylist;
		}
	}

	@SuppressWarnings("unchecked")
	public Song[] getPlaylist() {		
		
		try{
			Vector<Song> result = pandora.getPlaylist(idToken);
			Song[] list = new Song[result.size()];
			result.copyInto(list);
			currentPlaylist = list;
		}
		catch (Exception e){
			e.getMessage();
			e.getStackTrace();
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

//	public boolean isCreator() {
//		return isCreator;
//	}

	public boolean isQuickMix() {
		return isQuickMix;
	}
}
