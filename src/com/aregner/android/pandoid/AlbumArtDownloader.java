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


public class AlbumArtDownloader {
	
	public static final String SMALL = "small";
	public static final String MEDIUM = "medium";
	public static final String LARGE = "large";
	public static final String MEGA = "mega";
	
	private String apiKey = "3f6527e63fa7ab4771a687ce39377cf8";
	private String imageSize = LARGE;
	private String artist;
	private String album;
	
	public AlbumArtDownloader(String artist, String album){
		this.artist = artist;
		this.album = album;
		getAlbumUrl();
	}
	public AlbumArtDownloader(String artist, String album, String imageSize){
		this.artist = artist;
		this.album = album;
		this.imageSize = imageSize;
		getAlbumUrl();
	}
	
	public String getAlbumUrl()  {
		String imgUrl = null;

		String request = "http://ws.audioscrobbler.com/2.0/?method=album.getinfo&api_key="+apiKey;
		
        request += "&artist=" + artist.replaceAll(" ", "%20");
        request += "&album=" + album.replaceAll(" ", "%20");
        
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
}
