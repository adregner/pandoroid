package com.pandoroid.android;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.pandoroid.pandora.PandoraRadio;
import com.pandoroid.pandora.Song;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Dylan Powers <dylan.kyle.powers@gmail.com>
 * Description: This is a controller for the playback of songs.
 * 	All public methods (except run() of course) can be called and expected to
 *  have a near instantaneous return and hence are safe to run on the main thread
 *  of execution.
 */
public class MediaPlaybackController implements Runnable{	
	public MediaPlaybackController(String station_token, 
			                       String min_quality,
			                       String max_quality,
			                       PandoraRadio pandora_remote,
			                       ConnectivityManager net_connectivity) throws Exception{
		player_lock = new Object();
		quality_lock = new Object();
		m_active_song = new Song();
		

		m_play_queue = new LinkedList<Song>();
		m_pandora_remote = pandora_remote;
		m_net_conn = net_connectivity;
		m_station_token = station_token;
		m_stop_exchanger = new Exchanger<Boolean>();
		m_alive = Boolean.valueOf(false);
		m_need_next_song = Boolean.valueOf(false);
		m_pause = false;
		m_new_song_listener = new OnNewSongListener(){
								  		public void onNewSong(Song song){}
								                     };
        m_valid_play_command = Boolean.valueOf(false);								                     
 		setAudioQuality(min_quality, max_quality);
	}
	
	public void run(){
		instantiateInstance();
	
		synchronized(player_lock){
			m_player = new MediaPlayer();
			m_player.setOnCompletionListener(new MediaCompletionListener());
		}
		
		setAlive(true);
		
		setNeedNextSong(true);
		Boolean alive = Boolean.valueOf(true);
		while(alive.booleanValue()){
			if (m_net_conn.getActiveNetworkInfo().isConnected()){
				if (isPlayQueueLow()){
					pushMoreSongs();
				}
				if (isNewSongNeeded()){
					synchronized(player_lock){
						setNewSong();
					}
				}
			}
			
			synchronized(player_lock){
				if (!m_pause && m_player.isPlaying() == false && isPlayCommandValid()){
					m_player.start();
				}
			}
			
			try {	
				//Sleep for 1 second
				alive = m_stop_exchanger.exchange(alive, 1, TimeUnit.SECONDS); 
			} 
			catch (InterruptedException e) {} //We don't care
			catch (TimeoutException e) {} //Yes we do want this to happen
		}
		setPlayCommandValid(false);
		m_player.release();		
		setActiveSong(null);
		m_play_queue.clear();
	}
	
	public Song getSong(){
		//Only an exceptionally short lockup in exceptional instances
		//should ever occur here.
		return getActiveSong(); 
	}
	
	public boolean isAlive(){
		synchronized(m_alive){
			return m_alive.booleanValue();
		}
	}
	
	public void play(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				if (isPlayCommandValid()){
					synchronized(player_lock){	
						m_player.start();
						m_pause = false;
					}
				}
			}
		});
		
		t.start();
	}
	
	public void pause(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				if (isAlive()){
					synchronized(player_lock){
						//Pause commands are under all likely hood very urgent
						//and should be executed as soon as possible.
						m_pause = true;
						if (m_player.isPlaying()){
							m_player.pause();
						}						
					}
				}
			}
		});
		
		t.start();
	}
	
	public void reset(String station_token, PandoraRadio pandora_remote){
		Thread t = new Thread(new ResetThread(station_token, pandora_remote));
		t.start();
	}
	
	public void setAudioQuality(String min_quality, String max_quality) throws Exception{
		if (isValidAudioQualityRange(min_quality, max_quality)){
			Thread t = new Thread(new SetAudioQualityThread(min_quality, 
					                                        max_quality));
			t.start();
		}
		else {
			throw new Exception("Invalid audio quality range");
		}
	}
	
	/**
	 * Description: It is an error to call this after the thread has been started.
	 * @param listener
	 * @throws Exception 
	 */
	public void setOnNewSongListener(OnNewSongListener listener) throws Exception{
		if (!isAlive()){
			m_new_song_listener = listener;
		}
		else{
			throw new Exception("Illegal call to set the new song listener.");
		}
	}
	
	public void skip(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				if (isAlive()){
					synchronized(player_lock){					
						if (m_player.isPlaying()){
							m_player.pause(); //I'm thinking this should prevent
							                  //any "abruptness" in playback.
						}
					}				
					setNeedNextSong(true);
				}
			}
		});
		
		t.start();
	}
	
	public void stop(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				stopTask();
			}
		});	
		
		t.start();
	}
	



	/* private */
	private Object player_lock;
	private Object quality_lock;
	
	private Song m_active_song;
	private Boolean m_alive;
	
	private Exchanger<Boolean> m_stop_exchanger;
	
	private OnNewSongListener m_new_song_listener;
	private Boolean m_need_next_song;
	private String m_min_quality;
	private String m_max_quality;
	private PandoraRadio m_pandora_remote;
	private boolean m_pause;
	private LinkedList<Song> m_play_queue;
	private Thread m_running_playback_thread;
	private MediaPlayer m_player;
	private String m_station_token;
	private ConnectivityManager m_net_conn;
	private Boolean m_valid_play_command;
	
	private Song getActiveSong(){
		synchronized(m_active_song){
			return m_active_song;
		}
	}
	
	private void instantiateInstance(){
		if (isAlive()){
			stopTask();			
		}
		if (isAnotherInstanceRunning()){
			try {
				m_running_playback_thread.join();
			} catch (InterruptedException e) {}
		}
		m_running_playback_thread = Thread.currentThread();
	}
	
	private boolean isAnotherInstanceRunning(){
		if (m_running_playback_thread != null){
			if (m_running_playback_thread.isAlive()){
				return true;
			}			
		}
		
		return false;
	}
	
	private boolean isNewSongNeeded(){
		synchronized(m_need_next_song){			
			return m_need_next_song.booleanValue();
		}
	}
	
	private boolean isPlayCommandValid(){
		synchronized(m_valid_play_command){
			return m_valid_play_command.booleanValue();
		}
	}
	
	private boolean isPlayQueueLow(){
		return (m_play_queue.size() <= 1);
	}
	
	private boolean isValidAudioQualityRange(String min_quality, 
			                                 String max_quality) throws Exception{
		return (PandoraRadio.audioQualityCompare(min_quality, max_quality) >= 0);
	}
	
	private void prepareSong(){
		try {
			m_player.setDataSource(getActiveSong().getAudioUrl(m_max_quality));
			m_player.prepare();
			setPlayCommandValid(true);
		} 
		catch (IllegalArgumentException e) {
			Log.e("Pandoroid", e.getMessage(), e);
		}
		catch (SecurityException e) {} 
		catch (IllegalStateException e) {
			Log.e("Pandoroid", e.getMessage(), e);
		}
		catch (IOException e) {}
	}
	
	private void pushMoreSongs(){
		Vector<Song> new_songs = null;
		try{
			new_songs = m_pandora_remote.getPlaylist(m_station_token);
			for (int i = 0; i < new_songs.size(); ++i){
				m_play_queue.add(new_songs.get(i));
			}
		}
		catch (Exception e){
			Log.e("Pandoroid", e.getMessage(), e);
		}
	}
	
	private void setActiveSong(Song new_song){
		if (new_song == null){
			new_song = new Song();
		}
		synchronized(m_active_song){
			m_active_song = new_song;
		}
	}
	
	private void setAlive(boolean new_liveness){
		synchronized(m_alive){
			m_alive = Boolean.valueOf(new_liveness);
		}
	}
	
	/**
	 * Description:
	 * Precondition: m_play_queue is not null
	 */
	private void setNewSong(){
		setPlayCommandValid(false);
		m_player.reset();
		if (m_play_queue.peek() != null){
			setNeedNextSong(false);
			setActiveSong(m_play_queue.pollFirst());

			Handler handler = new Handler(Looper.getMainLooper());
			handler.post(new Runnable(){
				public void run(){		
					m_new_song_listener.onNewSong(getActiveSong());
				}
			});
			prepareSong();
		}
	}
	
	private void setNeedNextSong(boolean new_val){
		synchronized(m_need_next_song){
			m_need_next_song = Boolean.valueOf(new_val);
		}
	}
	
	private void setPlayCommandValid(boolean new_value){
		synchronized(m_valid_play_command){
			m_valid_play_command = Boolean.valueOf(new_value);
		}
	}
	
	/**
	 * @description
	 * Description: Will not return until the playback is stopped.
	 */
	private void stopTask(){
		if (isAlive()){
			setAlive(false);
			try {					
				m_stop_exchanger.exchange(false);
			} catch (InterruptedException e) {}
		}
	}
	
	private class MediaCompletionListener implements OnCompletionListener{
		public void onCompletion(MediaPlayer mp){
			setNeedNextSong(true);
		}
	}
	
	private class ResetThread implements Runnable{
		public String station_token;
		public PandoraRadio pandora_remote;
		
		public ResetThread(String station_token, PandoraRadio pandora_remote){
			this.station_token = station_token;
			this.pandora_remote = pandora_remote;
		}
		
		public void run(){
			stopTask();
			m_station_token = this.station_token;
			m_pandora_remote = this.pandora_remote;
		}
	}
	
	private class SetAudioQualityThread implements Runnable{
		public String min;
		public String max;
		
		public SetAudioQualityThread(String min_quality, String max_quality){
			this.min = min_quality;
			this.max = max_quality;
		}
		
		public void run(){
			synchronized(quality_lock){	
				m_min_quality = this.min;
				m_max_quality = this.max;
			}
		}
	}
	

	

}
