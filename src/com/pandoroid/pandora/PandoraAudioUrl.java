package com.pandoroid.pandora;

import java.util.Map;

/**
 * @author Dylan Powers <dylan.kyle.powers@gmail.com>
 * Description: Holds data relevant to the URL of a song retrieved from Pandora.
 *
 */
public class PandoraAudioUrl implements Comparable<PandoraAudioUrl>{
	public String m_type;
	public int m_bitrate;
	public String m_url;
	
	public PandoraAudioUrl(String type, int bitrate, String url){
		this.m_type = type;
		this.m_bitrate = bitrate;
		this.m_url = url;
	}
	
	public PandoraAudioUrl(Map<String, Object> extended_audio_url){
		if (is_AAC_64(extended_audio_url)){
			this.m_type = PandoraRadio.AAC_64;					
		}
		else if (is_MP3_192(extended_audio_url)){
			this.m_type = PandoraRadio.MP3_192;
		}
		else {
			this.m_type = "unknown";
		}
		
		this.m_bitrate = Integer.parseInt((String) extended_audio_url.get("bitrate"));
		this.m_url = (String) extended_audio_url.get("audioUrl");
	}
	
	private boolean is_AAC_64(Map<String, Object> extended_audio_url){
		return (((String) extended_audio_url.get("bitrate")).compareTo("64") == 0
				                          &&
				((String) extended_audio_url.get("encoding")).compareTo("aacplus") == 0);
	}
	
	private boolean is_MP3_192(Map<String, Object> extended_audio_url){
		return (((String) extended_audio_url.get("bitrate")).compareTo("192") == 0
							         &&
	            ((String) extended_audio_url.get("encoding")).compareTo("mp3") == 0);
	}
	
	public int compareTo(PandoraAudioUrl comparable){
		if (this.m_bitrate == comparable.m_bitrate){
			return 0;
		}
		else if (this.m_bitrate < comparable.m_bitrate){
			return -1;
		}
		else { //if it's greater
			return 1;
		}
	}
}
