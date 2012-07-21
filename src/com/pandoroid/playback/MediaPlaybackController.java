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

import com.pandoroid.pandora.PandoraAudioUrl;
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
	
	//For when available bandwidth is too low for the lowest quality.
	public static final int HALT_INSUFFICIENT_CONNECTIVITY = 4;
	
	public static final int MINIMUM_SONG_COMPLETENESS = 95;
	
	public MediaPlaybackController(String station_token, 
			                       String min_quality,
			                       String max_quality,
			                       PandoraRadio pandora_remote,
			                       ConnectivityManager net_connectivity) throws Exception{
 		//Lock initialization. They MUST always be first!
		player_lock = new Object();
		quality_lock = new Object();
		bandwidth_lock = new Object();
		
		
		m_pandora_remote = pandora_remote;
		m_net_conn = net_connectivity;
		m_station_token = station_token;
 		setAudioQuality(min_quality, max_quality);
		
		//Other generic initialization
		m_active_song = new Song();
		m_active_song_buffer_complete_flag = Boolean.valueOf(false);
		m_active_song_rebuffer_flag = Boolean.valueOf(false);
		m_active_song_url = new PandoraAudioUrl(max_quality, 0, null);
		m_alive = Boolean.valueOf(false);
		m_buffering_flag = Boolean.valueOf(false);
		m_need_next_song = Boolean.valueOf(false);
		m_pause = false;
		m_play_queue = new LinkedList<Song>();		
		m_playback_position = Integer.valueOf(0);
		m_stop_exchanger = new Exchanger<Boolean>();
        m_valid_play_command = Boolean.valueOf(false);	

        //Listener initialization
        m_error_listener = new OnErrorListener(){
        	public void onError(String error_message, 
        			            Throwable e, 
        			            boolean remote_error_flag,
        			            int rpc_error_code){}
		};
		m_new_song_listener = new OnNewSongListener(){
	  		public void onNewSong(Song song){}
		};
		m_playback_continued_listener = new OnPlaybackContinuedListener(){
			public void onPlaybackContinued(){}
		};
		m_playback_halted_listener = new OnPlaybackHaltedListener(){
			public void onPlaybackHalted(int halt_code, int countdown_time){}
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
			m_player = instantiateMediaPlayer();
		}
		
		synchronized(bandwidth_lock){
			m_bandwidth = new MediaBandwidthEstimator();
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
						if (!m_pause && isPlayCommandValid()){
							m_player.start();
						}
					}
				}
				else if (isRebufferNeeded()){
					synchronized(player_lock){
						synchronized(bandwidth_lock){
							m_bandwidth.reset();
						}
						rebufferSong(getOptimizedPandoraAudioUrl(getActiveSong()));
						if (!m_pause && isPlayCommandValid()){
							m_player.start();
						}
					}
				}
				else if (isBuffering()){
					synchronized(player_lock){
						setBuffering(false);
						adjustAudioQuality();
						if (!m_pause && isPlayCommandValid()){
							m_player.start();
						}
					}
				}
				

				
			}
			else {
				synchronized(bandwidth_lock){
					m_bandwidth.reset();
				}
				//no_network_flag = true;
			}
			
			synchronized(player_lock){
				if (!m_player.isPlaying() && !m_pause){
					m_playback_halted_flag = true;
				}
			}
			

			
			try {	
				//Sleep for 1 second
				alive = m_stop_exchanger.exchange(alive, 1, TimeUnit.SECONDS); 
			} 
			catch (InterruptedException e) {} //We don't care
			catch (TimeoutException e) {} //Yes we do want this to happen
		}
		
		//Cleanup!
		setPlayCommandValid(false);
		m_player.release();		
		setActiveSong(null);
		m_play_queue.clear();
		if (m_cached_player_ready_flag){
			m_cached_player.release();
		}
	}
	
	/**
	 * Description: Gets the quality string of the audio currently playing.
	 * @return
	 */
	public String getCurrentQuality(){
		return getCurrentUrl().m_type;
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
	 *  thread of execution when an error occurs.
	 * @param listener -Implements OnErrorListener
	 * @throws Exception -If the given listener is null, a generic exception 
	 * 	will be	thrown.
	 */
	public void setOnErrorListener(OnErrorListener listener) throws Exception{
		synchronized(m_error_listener){
			if (listener != null){
				m_error_listener = listener;
			}
			else{
				throw new Exception("Given listener is null!");
			}
		}
	}
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when a new song is played.
	 * @param listener -Implements OnNewSongListener
	 * @throws Exception -If the given listener is null, a generic exception 
	 * 	will be	thrown.
	 */
	public void setOnNewSongListener(OnNewSongListener listener) throws Exception{
		synchronized(m_new_song_listener){
			if (listener != null){
				m_new_song_listener = listener;
			}
			else{
				throw new Exception("Given listener is null!");
			}
		}
	}
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when playback continues after it has been halted.
	 * @param listener -Implements OnPlaybackContinued listener
	 * @throws Exception -If the given listener is null, a generic exception 
	 * 	will be	thrown.
	 */
	public void setOnPlaybackContinuedListener(OnPlaybackContinuedListener listener) throws Exception{
		synchronized(m_playback_continued_listener){
			if (listener != null){
				m_playback_continued_listener = listener;
			}
			else{
				throw new Exception("Given listener is null!");
			}
		}
	}
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when playback has been halted due to network
	 *  conditions.
	 * @param listener -Implements OnPlaybackHaltedListener
	 * @throws Exception -If the given listener is null, a generic exception 
	 * 	will be	thrown.
	 */
	public void setOnPlaybackHaltedListener(OnPlaybackHaltedListener listener) throws Exception{
		synchronized(m_playback_halted_listener){
			if (listener != null){
				m_playback_halted_listener = listener;
			}
			else{
				throw new Exception("Given listener is null!");
			}
		}
	}
	
	/**
	 * Description: Skips to the next song.
	 */
	public void skip(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				if (isAlive()){			
					setNeedNextSong(true);
					m_playback_engine_thread.interrupt();
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
	
	//A few locks for thread safety.
	private Object player_lock;
	private Object quality_lock;
	private Object bandwidth_lock;
	
	//Listeners
	private OnErrorListener m_error_listener;
	private OnNewSongListener m_new_song_listener;
	private OnPlaybackContinuedListener m_playback_continued_listener;
	private OnPlaybackHaltedListener m_playback_halted_listener;	
	
	//Other variables required for the controller to run.
	private Song m_active_song;
	private Boolean m_active_song_buffer_complete_flag;
	private Boolean m_active_song_rebuffer_flag;
	private int m_active_song_length;
	private int m_active_song_playback_pos;
	private PandoraAudioUrl m_active_song_url;
	//private LinkedList<Song> m_active_song_urls;
	private Boolean m_alive;
	private MediaBandwidthEstimator m_bandwidth;
	private Boolean m_buffering_flag;
	private MediaPlayer m_cached_player;
	private boolean m_cached_player_ready_flag;
	private String m_min_quality;
	private String m_max_quality;
	private Boolean m_need_next_song;
	private ConnectivityManager m_net_conn;
	private PandoraRadio m_pandora_remote;
	private boolean m_pause;
	private LinkedList<Song> m_play_queue;
	private boolean m_playback_halted_flag;
	private int m_playback_halted_reason;
	private Integer m_playback_position;
	private MediaPlayer m_player;

	private Thread m_playback_engine_thread;
	private Boolean m_restart_song_flag;
	private String m_station_token;
	private Exchanger<Boolean> m_stop_exchanger;
	private Boolean m_valid_play_command;
	
	
	private void adjustAudioQuality(){
		PandoraAudioUrl best_available_quality = getOptimizedPandoraAudioUrl(getActiveSong());
		try {
			if (PandoraRadio.audioQualityCompare(best_available_quality.m_type, getCurrentUrl().m_type) < 0){
				rebufferSong(best_available_quality);
			}
		} catch (Exception e) {
			Log.e("Pandoroid", e.getMessage(), e);
		}
		
	}
	
	/**
	 * Description: Thread safe accessor for m_active_song.
	 * @return
	 */
	private Song getActiveSong(){
		synchronized(m_active_song){
			return m_active_song;
		}
	}
	
	private PandoraAudioUrl getCurrentUrl(){
		synchronized(m_active_song_url){
			return m_active_song_url;
		}
	}
	
	private PandoraAudioUrl getOptimizedPandoraAudioUrl(Song song){
		int bitrate = 0;
		PandoraAudioUrl url = null;
		
		synchronized(bandwidth_lock){
			bitrate = m_bandwidth.getBitrate();
		}
			
		LinkedList<PandoraAudioUrl> urls = song.getSortedAudioUrls();
		for (int i = 0; i < urls.size(); ++i){
			PandoraAudioUrl tmp = urls.get(i);

			try {
				if ((tmp.m_bitrate < bitrate 
							&& 
					     PandoraRadio.audioQualityCompare(tmp.m_type, getMaxQuality()) <= 0)
				     	||
				    (PandoraRadio.audioQualityCompare(tmp.m_type, getMinQuality()) == 0)
				    	||
				    (bitrate == 0 
							&&
						PandoraRadio.audioQualityCompare(tmp.m_type, getMaxQuality()) == 0)
				   ){
					url = tmp;
					break;
				}
			} catch (Exception e) {
				Log.e("Pandoroid", e.getMessage(), e);
			}
		}

		if (url == null){
			url = new PandoraAudioUrl(getMaxQuality(), 0, song.getAudioUrl(getMaxQuality()));
			Log.e("Pandoroid", "Error acquiring a url for optimized playback. " +
					"Defaulting to max quality.", new Exception());
		}
		
		return url;
	}
	
	/**
	 * Description: Thread safe accessor for m_error_listener.
	 * @return The error listener.
	 */
	private OnErrorListener getErrorListener(){
		synchronized(m_error_listener){
			return m_error_listener;
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
	 * Description: Thread safe accessor for m_new_song_listener.
	 * @return The new song listener.
	 */
	private OnNewSongListener getNewSongListener(){
		synchronized(m_new_song_listener){
			return m_new_song_listener;
		}
	}
	
	/**
	 * Description: Thread safe accessor for m_playback_continued_listener.
	 * @return The playback continued listener.
	 */
	private OnPlaybackContinuedListener getPlaybackContinuedListener(){
		synchronized(m_playback_continued_listener){
			return m_playback_continued_listener;
		}
	}
	
	
	
	/**
	 * Description: Thread safe accessor for m_playback_halted_listener.
	 * @return The playback halted listener.
	 */
	private OnPlaybackHaltedListener getPlaybackHaltedListener(){
		synchronized(m_playback_halted_listener){
			return m_playback_halted_listener;
		}
	}
	
	private int getPlaybackPosition(){
		synchronized(m_playback_position){
			return m_playback_position.intValue();
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
				m_playback_engine_thread.join();
			} catch (InterruptedException e) {}
		}
		m_playback_engine_thread = Thread.currentThread();
	}
	
	/**
	 * Description: Creates a new MediaPlayer.
	 * @return The newly created MediaPlayer.
	 */
	private MediaPlayer instantiateMediaPlayer(){
		MediaPlayer media_player = new MediaPlayer();
		media_player.setOnCompletionListener(new MediaCompletionListener());
		media_player.setOnBufferingUpdateListener(new MediaBufferingUpdateListener());
		media_player.setOnErrorListener(new MediaErrorListener());
		media_player.setOnInfoListener(new MediaInfoListener());

		
		return media_player;
	}
	
	/**
	 * Description: Checks to make sure another thread doesn't exist with 
	 * 	the controller running.
	 * @return
	 */
	private boolean isAnotherInstanceRunning(){
		if (m_playback_engine_thread != null){
			if (m_playback_engine_thread.isAlive()){
				return true;
			}			
		}
		
		return false;
	}
	
	private boolean isBufferComplete(){
		synchronized(m_active_song_buffer_complete_flag){
			return m_active_song_buffer_complete_flag.booleanValue();
		}
	}
	
	private boolean isBuffering(){
		synchronized(m_buffering_flag){
			return m_buffering_flag.booleanValue();
		}
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
	
	private boolean isPlaybackComplete(){
		
		//Some would say that being within 5 seconds of the end is good enough
		int end_song_position = (int) ((m_active_song_length - 
											(
												m_active_song_length * 
												((float) 1F/MINIMUM_SONG_COMPLETENESS)
											)
									   )/1000F);
		if (getPlaybackPosition()/1000 < end_song_position){
			return false;
		}
		return true;
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
	
	private boolean isRebufferNeeded(){
		synchronized(m_active_song_rebuffer_flag){
			return m_active_song_rebuffer_flag.booleanValue();
		}
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
		return (PandoraRadio.audioQualityCompare(max_quality, min_quality) >= 0);
	}
	
	/**
	 * Description: Prepares a song so it can be played by the media player.
	 */
	private void prepareSong(){
		setBufferComplete(false);
		if (m_cached_player_ready_flag){
			m_player = m_cached_player;
			setPlayCommandValid(true);
			m_cached_player.release();
			m_cached_player_ready_flag = false;
		}
		else{
			try {
				setCurrentUrl(getOptimizedPandoraAudioUrl(getActiveSong()));
				Log.i("Pandoroid", "Current Audio Quality: " + getCurrentQuality());
				m_player.setDataSource(getCurrentUrl().m_url);
				m_player.prepare();
				setPlayCommandValid(true);
			} 
			catch (IllegalArgumentException e) {
				Log.e("Pandoroid", e.getMessage(), e);
			}
			catch (SecurityException e) {
				Log.e("Pandoroid", e.getMessage(), e);
			} 
			catch (IllegalStateException e) {
				Log.e("Pandoroid", e.getMessage(), e);
			}
			catch (IOException e) {
				Log.e("Pandoroid", e.getMessage(), e);
			}
		}
	}
	
	private void prepCachedPlayer(){
		
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
	
	private void rebufferSong(PandoraAudioUrl url){
		setPlayCommandValid(false);
		m_player.reset();
		setBufferComplete(false);
		setNeedRebuffer(false);
		
		try {
			//m_player.setDataSource(getActiveSong().getAudioUrl(getMinQuality()));
			m_player.setDataSource(url.m_url);
			setCurrentUrl(url);
			Log.i("Pandoroid", "Current Audio Quality: " + getCurrentQuality());
			m_player.prepare();
			m_player.seekTo(getPlaybackPosition());
			setPlayCommandValid(true);
		} 
		catch (IllegalArgumentException e) {
			Log.e("Pandoroid", e.getMessage(), e);
			setNeedNextSong(true);
		}
		catch (SecurityException e) {
			Log.e("Pandoroid", e.getMessage(), e);
			setNeedNextSong(true);
		} 
		catch (IllegalStateException e) {
			Log.e("Pandoroid", e.getMessage(), e);
			setNeedNextSong(true);
		}
		catch (IOException e) {
			Log.e("Pandoroid", e.getMessage(), e);
			setNeedNextSong(true);
		}
	}
	
	
	private void sendErrorNotification(String error_message,
									   Throwable e,
									   boolean remote_error_flag,
									   int rpc_error_code){
		
	}
	
	private void sendNewSongNotification(Song song){
		Handler handler = new Handler(Looper.getMainLooper());
		SendNewSongNotificationTask task = new SendNewSongNotificationTask(song);
		handler.post(task);
	}
	
	
	private void sendPlaybackHaltedNotification(int halt_code, int countdown_time){
		
	}
	
	private void sendPlaybackContinuedNotification(){
		
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
	
	private void setBufferComplete(boolean bool){
		synchronized(m_active_song_buffer_complete_flag){
			m_active_song_buffer_complete_flag = Boolean.valueOf(bool);
		}
	}
	
	private void setBuffering(boolean bool){
		synchronized(m_buffering_flag){
			m_buffering_flag = Boolean.valueOf(bool);
		}
	}

	private void setCurrentUrl(PandoraAudioUrl url){
		synchronized(m_active_song_url){
			m_active_song_url = url;
		}		
	}
	
	private void setHaltCode(int new_code){
		
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

			sendNewSongNotification(getActiveSong());
//			Handler handler = new Handler(Looper.getMainLooper());
//			handler.post(new Runnable(){
//				public void run(){		
//					getNewSongListener().onNewSong(getActiveSong());
//				}
//			});
			prepareSong();
			m_active_song_length = m_player.getDuration();
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
	
	private void setPlaybackPosition(int pos){
		synchronized(m_playback_position){
			m_playback_position = Integer.valueOf(pos);
		}
	}
	
	private void setNeedRebuffer(boolean bool){
		synchronized(m_active_song_rebuffer_flag){
			m_active_song_rebuffer_flag = Boolean.valueOf(bool);
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
	
	private class BandwidthUpdaterThread extends Thread{
		
		public BandwidthUpdaterThread(MediaPlayer mp, 
									  int percent, 
									  int media_length, 
									  int bitrate){
			this.m_mp = mp;
			this.m_percent = percent;
			this.m_length = media_length;
			this.m_bitrate = bitrate;
		}
		
		public void run(){			
			synchronized(bandwidth_lock){
				
				//The OnBufferingUpdateListener has a tendency to spout out 100% at 
				//impossible situations such as before a song has even started
				//rather than a 0%. Seems like a bug.
				if (this.m_mp == m_player 
							&&
					this.m_percent == 100 
							&& 
					m_bandwidth.doesIdExist(this.m_mp.getAudioSessionId())
					){		
					Log.d("Pandoroid", 
							  "Buffer: " + Integer.toString(this.m_percent) + "%"
						 );
					setBufferComplete(true);
				}
				
				m_bandwidth.update(this.m_mp.getAudioSessionId(), 
						           this.m_percent, 
						           this.m_length, 
						           this.m_bitrate);
			}
		}
		
		private MediaPlayer m_mp;
		private int m_percent;
		private int m_length;
		private int m_bitrate;		
	}
	
	private class MediaBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener{
		
		public void onBufferingUpdate(MediaPlayer mp, int percent){
			
			//Let's be sure to keep our UI snappy!
			BandwidthUpdaterThread t = new BandwidthUpdaterThread(mp, 
			                                                      percent, 
			                                                      m_active_song_length, 
			                                                      getCurrentUrl().m_bitrate);
			t.start();
		}
	}
	
	/**
	 * Description: Implements the OnCompletionListener for the media player.
	 */
	private class MediaCompletionListener implements MediaPlayer.OnCompletionListener{
		public void onCompletion(MediaPlayer mp){
			if (mp == m_player){
				setPlaybackPosition(mp.getCurrentPosition());
				if (isPlaybackComplete()){
					setNeedNextSong(true);
				}
				else{
					setNeedRebuffer(true);
					setPlaybackPosition(mp.getCurrentPosition());
				}
				m_playback_engine_thread.interrupt();
			}
		}
	}
	
	//Epicly useless!!!
	private class MediaErrorListener implements MediaPlayer.OnErrorListener{
		public boolean onError(MediaPlayer mp, int what, int extra){
//			if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED){
//				Log.e("Pandoroid", "No internets!", new Exception());
//				m_playback_halted_listener.onPlaybackHalted(HALT_STATE_NO_INTERNET, -1);
//
//			}
//			else {
			Log.e("Pandoroid", "Unknown MediaPlayer error", new Exception());
			m_error_listener.onError("An unknown MediaPlayer error occured",
					                 new Exception(), false, -1);

			//}
			
			return true;
		}
	}
	
	private class MediaInfoListener implements MediaPlayer.OnInfoListener{
		public boolean onInfo(MediaPlayer mp, int what, int extra){
			
			//No fancy main thread stuff is needed here because we're already
			//in the main thread haha.
			if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START){
				m_playback_halted_listener.onPlaybackHalted(HALT_STATE_BUFFERING, -1);
				setPlaybackPosition(mp.getCurrentPosition());
				setBuffering(true);
			}
			else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END){
				m_playback_continued_listener.onPlaybackContinued();
			}
			return true;
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
	
	private class SendNewSongNotificationTask implements Runnable{
		public SendNewSongNotificationTask(Song song){
			this.m_song = song;
		}
		
		public void run(){
			getNewSongListener().onNewSong(this.m_song);
		}
		
		private Song m_song;
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
