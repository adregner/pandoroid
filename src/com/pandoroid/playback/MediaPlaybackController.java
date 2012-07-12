/* This file is part of Pandoroid
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

package com.pandoroid.playback;

import android.content.Context;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pandoroid.pandora.PandoraRadio;
import com.pandoroid.pandora.Song;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Description: This is a controller for the playback of songs (If a person 
 *  wanted to it probably wouldn't be too hard to make it use generic types 
 *  and feed it any kind of media that the Android media player supports).
 * 	All public methods (except run() of course) can be called and expected to
 *  have a near instantaneous return and hence are safe to run on the main 
 *  thread of execution. Some extensibility ideas are to set more listeners, 
 *  and actually implement the continuous playback engine.
 * @author Dylan Powers <dylan.kyle.powers@gmail.com>
 */
public class MediaPlaybackController implements Runnable{	
	
	/* 
	 * Public 
	 */
	
	public static final int HALT_STATE_NO_NETWORK = 0;
	public static final int HALT_STATE_NO_INTERNET = 1;
	public static final int HALT_STATE_BUFFERING = 2;
	
	//This is for when a switch of networks occurs and the whole song has to
	//be buffered over again.
	public static final int HALT_STATE_REBUFFERING = 3; 
	
	public MediaPlaybackController(String station_token, 
			                       String min_quality,
			                       String max_quality,
			                       PandoraRadio pandora_remote,
			                       ConnectivityManager net_connectivity) throws Exception{
		m_pandora_remote = pandora_remote;
		m_net_conn = net_connectivity;
		m_station_token = station_token;
 		setAudioQuality(min_quality, max_quality);
 		
 		//Lock initialization
		player_lock = new Object();
		quality_lock = new Object();
		
		//Other generic initialization
		m_active_song = new Song();
		m_alive = Boolean.valueOf(false);
		m_need_next_song = Boolean.valueOf(false);
		m_pause = false;
		m_play_queue = new LinkedList<Song>();		
		m_stop_exchanger = new Exchanger<Boolean>();
        m_valid_play_command = Boolean.valueOf(false);	

        //Listener initialization
		m_new_song_listener = new OnNewSongListener(){
								  		public void onNewSong(Song song){}
								                     };
							                     

	}
	
	/**
	 * Description: This is what the thread calls when started. It is the engine
	 * 	behind the media playback controller.
	 */
	public void run(){
		NetworkInfo active_network_info;
		
		instantiateInstance();
	
		synchronized(player_lock){
			m_player = new MediaPlayer();
			m_player.setOnCompletionListener(new MediaCompletionListener());
			m_player.setOnBufferingUpdateListener(new OnBufferingUpdateListener(){
				public void onBufferingUpdate(MediaPlayer mp, int percent){
					Log.d("Pandoroid", Long.toString(System.currentTimeMillis()/1000L) + 
							          ": The song \"" + getSong().getTitle() + 
							           "\" is buffered to " + Integer.toString(percent) +
							           "%");
				}
			});
		}
		
		setAlive(true);
		
		setNeedNextSong(true);
		Boolean alive = Boolean.valueOf(true);
		while(alive.booleanValue()){
			
			//Prevent a null pointer exception in case an active network is not
			//available.
			active_network_info = m_net_conn.getActiveNetworkInfo();
			if (active_network_info != null && active_network_info.isConnected()){
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
	
	/**
	 * Description: Gets the quality string of the audio currently playing.
	 * @return
	 */
	public String getCurrentQuality(){
		return getMaxQuality();
	}
	
	/**
	 * Description: Does what it says and gets the song that's currently playing.
	 * @return
	 */
	public Song getSong(){
		//Only an exceptionally short lockup in exceptional instances
		//should ever occur here.
		return getActiveSong(); 
	}
	
	/**
	 * Description: Checks if the playback controller is alive, or in other 
	 *  terms if the thread has been started and the stop command hasn't been 
	 *  called. The majority of commands only have significance when this is 
	 *  true.
	 * @return
	 */
	public boolean isAlive(){
		synchronized(m_alive){
			return m_alive.booleanValue();
		}
	}
	
	/**
	 * Description: Starts/resumes media playback.
	 */
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
	
	/**
	 * Description: Pauses media playback.
	 */
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
	
	/**
	 * Description: Resets the media playback controller to use the specified
	 * 	station_token, and the updated PandoraRadio instance. Note that this
	 * 	doesn't actually start the controller back up. That must be done 
	 * 	externally.
	 * @param station_token
	 * @param pandora_remote
	 */
	public void reset(String station_token, PandoraRadio pandora_remote){
		Thread t = new Thread(new ResetThread(station_token, pandora_remote));
		t.start();
	}
	
	/**
	 * Description: Sets the audio quality range to be played. Set both the same
	 * 	for constant quality playback. This is thread safe and can be called at
	 * 	any moment (the controller can be dead or alive).
	 * @param min_quality
	 * @param max_quality
	 * @throws Exception
	 */
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
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when a new song is played.
	 *  It is an error to call this after the playback controller
	 *  has been started (because we're too lazy to make this thread safe haha). 
	 *  This function can only be called when isAlive() returns false.
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
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when playback continues after it has been halted.
	 *  It is an error to call this after the playback controller
	 *  has been started (because we're too lazy to make this thread safe haha). 
	 *  This function can only be called when isAlive() returns false.
	 * @param listener
	 * @throws Exception
	 */
	public void setOnPlaybackContinuedListener(OnPlaybackContinuedListener listener) throws Exception{
		if (!isAlive()){
			//listener goes here
		}
		else{
			throw new Exception("Illegal call to set the playback continued listener.");
		}
	}
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when playback has been halted due to network
	 *  conditions.
	 *  It is an error to call this after the playback controller
	 *  has been started (because we're too lazy to make this thread safe haha). 
	 *  This function can only be called when isAlive() returns false.
	 * @param listener
	 * @throws Exception
	 */
	public void setOnPlaybackHaltedListener(OnPlaybackHaltedListener listener) throws Exception{
		if (!isAlive()){
			//listener goes here
		}
		else{
			throw new Exception("Illegal call to set the playback halted listener.");
		}
	}
	
	/**
	 * Description: Skips to the next song.
	 */
	public void skip(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				if (isAlive()){
					synchronized(player_lock){					
						if (m_player.isPlaying()){
							m_player.pause(); //I'm thinking this should prevent
							                  //any "abruptness" in playback...
							                  //or I could be full of beans.
											  // --Dylan
						}
					}				
					setNeedNextSong(true);
				}
			}
		});
		
		t.start();
	}
	
	/**
	 * Description: Stops media playback, and the controller. The only way to
	 * 	restart playback and the controller is to restart the thread it resides
	 *  in.
	 */
	public void stop(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				stopTask();
			}
		});	
		
		t.start();
	}
	/* End Public */
	


	/*
	 * Private 
	 */
	
	//Our locks for thread safety.
	private Object player_lock;
	private Object quality_lock;
	
	//Other variables required for the controller to run.
	private Song m_active_song;
	private Boolean m_alive;
	private String m_min_quality;
	private String m_max_quality;
	private OnNewSongListener m_new_song_listener;
	private Boolean m_need_next_song;
	private ConnectivityManager m_net_conn;
	private PandoraRadio m_pandora_remote;
	private boolean m_pause;
	private LinkedList<Song> m_play_queue;
	private MediaPlayer m_player;
	private Thread m_running_playback_thread;
	private String m_station_token;
	private Exchanger<Boolean> m_stop_exchanger;
	private Boolean m_valid_play_command;
	
	/**
	 * Description: Thread safe accessor for m_active_song.
	 * @return
	 */
	private Song getActiveSong(){
		synchronized(m_active_song){
			return m_active_song;
		}
	}
	
	/**
	 * Description: Thread safe accessor for m_max_quality.
	 * @return
	 */
	private String getMaxQuality(){
		synchronized(quality_lock){
			return m_max_quality;
		}
	}
	
	/**
	 * Description: Thread safe accessor for m_min_quality.
	 */
	private String getMinQuality(){
		synchronized(quality_lock){
			return m_min_quality;
		}
	}
	
	/**
	 * Description: Upon start of the controller, some threading prep work needs
	 *  to be done before it can fully run. This function provides that prep 
	 *  work.
	 */
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
	
	/**
	 * Description: Checks to make sure another thread doesn't exist with 
	 * 	the controller running.
	 * @return
	 */
	private boolean isAnotherInstanceRunning(){
		if (m_running_playback_thread != null){
			if (m_running_playback_thread.isAlive()){
				return true;
			}			
		}
		
		return false;
	}
	
	/**
	 * Description: Thread safe method of inquiring if another song is needed.
	 * @return
	 */
	private boolean isNewSongNeeded(){
		synchronized(m_need_next_song){			
			return m_need_next_song.booleanValue();
		}
	}
	
	/**
	 * Description: Thread safe method of inquiring if a play command to the 
	 * 	media player is valid.
	 * @return
	 */
	private boolean isPlayCommandValid(){
		synchronized(m_valid_play_command){
			return m_valid_play_command.booleanValue();
		}
	}
	
	/**
	 * Description: Checks to see if the play queue is low and should be 
	 * 	refilled.
	 * @return
	 */
	private boolean isPlayQueueLow(){
		return (m_play_queue.size() <= 1);
	}
	
	/**
	 * Description: Checks to see if two given audio format strings are of a 
	 * 	valid quality range. This is false if min_quality is greater than
	 *  max_quality.
	 * @param min_quality
	 * @param max_quality
	 * @return
	 * @throws Exception if the strings aren't audio_quality strings as defined
	 * 	by the constants in the class PandoraRadio.
	 */
	private boolean isValidAudioQualityRange(String min_quality, 
			                                 String max_quality) throws Exception{
		return (PandoraRadio.audioQualityCompare(min_quality, max_quality) >= 0);
	}
	
	/**
	 * Description: Prepares a song so it can be played by the media player.
	 */
	private void prepareSong(){
		try {
			m_player.setDataSource(getActiveSong().getAudioUrl(getMaxQuality()));
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
	
	/**
	 * Description: Pushes more songs to the play queue.
	 */
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
	
	/**
	 * Description: Thread safe method of setting the active song. If new song
	 * 	is null, then an empty song will be added to keep m_active_song from
	 * 	being null.
	 * @param new_song
	 */
	private void setActiveSong(Song new_song){
		if (new_song == null){
			new_song = new Song();
		}
		synchronized(m_active_song){
			m_active_song = new_song;
		}
	}
	
	/**
	 * Description: Thread safe method for setting m_alive.
	 * @param new_liveness
	 */
	private void setAlive(boolean new_liveness){
		synchronized(m_alive){
			m_alive = Boolean.valueOf(new_liveness);
		}
	}
	
	/**
	 * Description: Sets a new song ready for playback.
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
	
	/**
	 * Description: Thread safe method of setting m_need_next_song.
	 * @param new_val
	 */
	private void setNeedNextSong(boolean new_val){
		synchronized(m_need_next_song){
			m_need_next_song = Boolean.valueOf(new_val);
		}
	}
	
	/**
	 * Description: Thread safe method of setting m_valid_play_command.
	 * @param new_value
	 */
	private void setPlayCommandValid(boolean new_value){
		synchronized(m_valid_play_command){
			m_valid_play_command = Boolean.valueOf(new_value);
		}
	}
	
	/**
	 * Description: Does the job of stopping the playback controller. It will
	 *  not return until the playback is stopped, but that doesn't necessarily
	 *  mean the controller thread has ended.
	 */
	private void stopTask(){
		if (isAlive()){
			setAlive(false);
			try {					
				m_stop_exchanger.exchange(false);
			} catch (InterruptedException e) {}
		}
	}
	
	/**
	 * Description: Implements the OnCompletionListener for the media player.
	 */
	private class MediaCompletionListener implements OnCompletionListener{
		public void onCompletion(MediaPlayer mp){
			setNeedNextSong(true);
		}
	}
	
	/**
	 * Description: A runnable class for resetting the controller with the new
	 * 	values.
	 */
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
	
	/**
	 * Description: A runnable class that's thread safe and sets the audio 
	 * 	quality minimum and maximum.
	 */
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
	
	/* End Private */
}
