package com.aregner.android.pandoid;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import android.util.Log;

import com.aregner.pandora.Song;


public class AlbumArtDownloader {
	
	public static final String SMALL = "small";
	public static final String MEDIUM = "medium";
	public static final String LARGE = "large";
	public static final String XLARGE = "extralarge";
	public static final String MEGA = "mega";	
	
	public static final int PREF_NORMAL = 0;
	public static final int PREF_HIRES = 1;
	public static final int PREF_MEGA = 2;
	
	
	private static final int ALBUM_ARTIST = 1; //look for album art with the album and the artist
	private static final int ARTIST_TITLE = 2; //look for album art using the artist and title
	
	private static final String LOG_TAG = "AlbumArtDownloader";
	
	private String apiKey = "3f6527e63fa7ab4771a687ce39377cf8";
	private String artist, title, album;
	
	private Map<String, String> urls = new HashMap<String, String>();
	
	public AlbumArtDownloader(Song song){
		String request;
		artist = song.getArtist();
		title = song.getTitle();
		album = song.getAlbum();
		
		request = formRequest(ALBUM_ARTIST);
		if(!sendRequest(request)) {
			request = formRequest(ARTIST_TITLE);
			sendRequest(request);
		}
	}
	
	private String formRequest(int method){
		String methodCall, request, parameters = "";

		if(method == ALBUM_ARTIST) {
			methodCall = "album.getinfo";
			parameters += "&album=" + album.replaceAll(" ", "%20");
		}
		else if(method == ARTIST_TITLE) {
			methodCall = "track.getinfo";
			Log.i("PandoidPlayer", "Could not find using album and artist, using track and artist");
			parameters += "&track=" + title.replaceAll(" ", "%20");
		}
		else {
			return null;
		}
		
		parameters +="&artist=" + artist.replaceAll(" ", "%20");
		request = "http://ws.audioscrobbler.com/2.0/?method=" + methodCall + "&api_key="+apiKey;
		request += parameters;
		
		Log.i(LOG_TAG, request);
		return request;
	}
	
	private boolean sendRequest(String request) {
		String imgUrl = null;
		boolean success = true;
        try {
        	URL url = new URL(request);
            InputStream is = url.openStream();
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(is);
            
            NodeList nl = doc.getElementsByTagName("image");
            for (int i = 0; i < nl.getLength(); i++) {
            	
                Node n = nl.item(i);
                String imageSize = n.getAttributes().item(0).getNodeValue();
                Node fc = n.getFirstChild();
              
                if (fc != null){
                	imgUrl = fc.getNodeValue();
                	
                	if(imgUrl.trim().length() > 0) {
                		//get rid of urls that the app passes when there is no artwork available: noimage
                		//and if you get repeated bad requests: /serve/174s
                		if(!imgUrl.contains("noimage")) {
                			urls.put(imageSize, imgUrl);
                		}
                	}
                }
            }
            if(urls.size() == 0){
            	success = false;
            }
        }
        catch (Exception e) {
        	e.printStackTrace();
        	success = false;
        }
        return success;
	}
	
	public String getAlbumUrl(String exactSize)  {
		Log.i(LOG_TAG,"Returning url:" + urls.get(exactSize));
		return urls.get(exactSize);
	}

	public String getAlbumUrl(int preference){
		if(preference == PREF_NORMAL){
			return urls.get(LARGE);
		}
		else if(preference == PREF_HIRES){
			if(urls.get(XLARGE)!= null){
				return urls.get(XLARGE);
			}
			else if(urls.get(LARGE) != null){
				return urls.get(LARGE);
			}
		}
		else if(preference == PREF_MEGA) {
			if(urls.get(MEGA) != null){
				return urls.get(MEGA);
			}
			else if(urls.get(XLARGE)!= null){
				return urls.get(XLARGE);
			}
			else if(urls.get(LARGE) != null) {
				return urls.get(LARGE);
			}
			else return null;
		}
		return null;	
	}
}
