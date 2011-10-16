package com.aregner.android.pandoid;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.aregner.pandora.Song;


public class AlbumArtDownloader {
	
	public static final String SMALL = "small";
	public static final String MEDIUM = "medium";
	public static final String LARGE = "large";
	public static final String MEGA = "mega";
	
	public static final int ALBUM_ARTIST = 1;
	public static final int ARTIST_TITLE = 2;
	
	private String apiKey = "3f6527e63fa7ab4771a687ce39377cf8";
	private String imageSize = LARGE;
	private String artist, title, album;
	private String url;
	
	public AlbumArtDownloader(Song song, String imageSize){
		artist = song.getArtist();
		title = song.getTitle();
		album = song.getAlbum();
		
		this.imageSize = imageSize;
		
		url = processRequest(ALBUM_ARTIST);
		
		if(url == null){
			url = processRequest(ARTIST_TITLE);
		}
		
	}
	
	private String processRequest(int method){
		String imgUrl = null;
		String methodCall, request, parameters = "";

		if(method == ALBUM_ARTIST) {
			methodCall = "album.getinfo";
			parameters += "&album=" + album.replaceAll(" ", "%20");
		}
		else if(method == ARTIST_TITLE) {
			methodCall = "track.getinfo";
			parameters += "&track=" + album.replaceAll(" ", "%20");
		}
		else {
			return null;
		}
		
		parameters +="&artist=" + artist.replaceAll(" ", "%20");

		request = "http://ws.audioscrobbler.com/2.0/?method=" + methodCall + "&api_key="+apiKey;
		request += parameters;
        
        try {
        	URL url = new URL(request);
            InputStream is = url.openStream();
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(is);
            
            NodeList nl = doc.getElementsByTagName("image");
            for (int i = 0; i < nl.getLength(); i++) {
            	
                Node n = nl.item(i);
                
                if (n.getAttributes().item(0).getNodeValue().equals(imageSize)) {
                    Node fc = n.getFirstChild();
                    
                    if (fc == null) 
                    	return null;
                    
                    imgUrl = fc.getNodeValue();
                    
                    if (imgUrl.trim().length() == 0)
                    	return null;
                }
            }
        }
        catch (Exception e) {
        	e.printStackTrace();
        }
       
		return imgUrl;
	}
	
	public  String getAlbumUrl()  {
		return url;
	}
}
