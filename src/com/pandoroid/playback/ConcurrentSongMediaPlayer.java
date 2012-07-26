package com.pandoroid.playback;

import java.io.IOException;

import com.pandoroid.pandora.PandoraAudioUrl;
import com.pandoroid.pandora.Song;

import android.media.MediaPlayer;

public class ConcurrentSongMediaPlayer{
	public static final int MINIMUM_SONG_COMPLETENESS = 95;
	
	public volatile Boolean m_buffer_complete_flag;
	
	public ConcurrentSongMediaPlayer(){
		m_player = new MediaPlayer();
		m_alive = false;
		m_buffering_counter = -1;
	}
	
	public ConcurrentSongMediaPlayer(Song song){
		m_player = new MediaPlayer();
		setSong(song);
	}
	
	public ConcurrentSongMediaPlayer(Song song, MediaPlayer mp){
		m_player = mp;
		setSong(song);
	}
	
	public void copy(ConcurrentSongMediaPlayer other_player){
		if (this != other_player){
			release();
			synchronized(this){
				m_player = other_player.getPlayer();
			}
			m_song = other_player.getSong();
			m_buffer_complete_flag = other_player.m_buffer_complete_flag;
			m_alive = other_player.m_alive;
		}
	}
	
	
	public int getAudioSessionId(){
		synchronized(this){
			return m_player.getAudioSessionId();
		}
	}
	
	public int getCurrentPosition(){
		synchronized(this){
			return m_player.getCurrentPosition();
		}
	}
	
	public int getDuration(){
		synchronized(this){
			return m_player.getDuration();
		}
	}
	
	public MediaPlayer getPlayer(){
		synchronized(this){
			return m_player;
		}
	}
	
	public Song getSong(){
		return m_song;
	}
	

	public PandoraAudioUrl getUrl(){
		return m_url;
	}
	
	/**
	 * Description: If this player is in fact buffering, then for every 5 calls,
	 * 	it will return true;
	 * @return
	 */
	public boolean isBuffering(){
		synchronized(buffer_lock){
			if (m_buffering_counter > 0){
				--m_buffering_counter;
			}
			else if(m_buffering_counter == 0){
				m_buffering_counter = 4;
				return true;
			}
		}
		
		return false;
	}
	
	public boolean isPlaybackComplete(){
		int song_length = getDuration();
		int current_pos = getCurrentPosition();
		
		int end_song_position = (int) ((song_length - 
				                        (
					song_length * ((float) 1F/MINIMUM_SONG_COMPLETENESS)
				                        )) / 1000F);
		
		if ((current_pos / 1000) < end_song_position){
			return false;
		}
		
		return true;
	}
	
	public boolean isPlaying(){
		synchronized(this){
			return m_player.isPlaying();
		}
	}
	
	public void pause(){
		synchronized(this){
			m_player.pause();
		}
	}
	
	/**
	 * Description: This is an all in one function that resets, sets the data 
	 * 	source, prepares, and if appropriate seeks to the appropriate position 
	 *  for the MediaPlayer. After a successful call, start can be called.
	 * @param url
	 * @throws IllegalArgumentException
	 * @throws SecurityException
	 * @throws IllegalStateException
	 * @throws IOException
	 */
	public void prepare(PandoraAudioUrl url) throws IllegalArgumentException, 
													SecurityException, 
													IllegalStateException, 
													IOException{
		setUrl(url);
		int prev_playback_pos = -1;
		if (m_alive){
			prev_playback_pos = getCurrentPosition();
		}
		reset();
		
		synchronized(this){
			m_player.setDataSource(url.toString());
			m_player.prepare();
			if (prev_playback_pos > 0){
				m_player.seekTo(prev_playback_pos);
			}
		}
		m_alive = true;
		setBuffering(false);
	}
	
	public void release(){
		synchronized(this){
			m_player.release();
		}
		m_alive = false;
	}
	
	public void reset(){
		synchronized(this){
			m_player.reset();
		}
		m_alive = false;
	}
	

	
	public void seekTo(int msec){
		synchronized(this){
			m_player.seekTo(msec);
		}
	}
	
	public void setBuffering(boolean bool){
		synchronized(buffer_lock){
			if (bool){
				m_buffering_counter = 0;
			}
			else{
				m_buffering_counter = -1;
			}
		}
	}
	
	public void setOnBufferingUpdateListener(MediaPlayer.OnBufferingUpdateListener listener){
		synchronized(this){
			m_player.setOnBufferingUpdateListener(listener);
		}
	}
	
	public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener){
		synchronized(this){
			m_player.setOnCompletionListener(listener);
		}
	}
	
	public void setOnErrorListener(MediaPlayer.OnErrorListener listener){
		synchronized(this){
			m_player.setOnErrorListener(listener);
		}
	}
	
	public void setOnInfoListener(MediaPlayer.OnInfoListener listener){
		synchronized(this){
			m_player.setOnInfoListener(listener);
		}
	}
	
	public void setSong(Song song){
		m_song = song;
		m_buffer_complete_flag = false;
		m_buffering_counter = -1;
		m_playback_complete_flag = false;
		reset();
	}
	
	public void start(){
		synchronized(this){
			m_player.start();
		}
	}
	
	private final Object buffer_lock = new Object();
	
	private Boolean m_alive;	
	private volatile int m_buffering_counter;
	private volatile Song m_song;
	private volatile PandoraAudioUrl m_url;
	private volatile Boolean m_playback_complete_flag;
	private MediaPlayer m_player;
	
	private void setUrl(PandoraAudioUrl url){
		m_url = url;
	}
	
}
