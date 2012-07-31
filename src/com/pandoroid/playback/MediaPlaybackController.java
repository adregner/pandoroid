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
import com.pandoroid.pandora.RPCException;
import com.pandoroid.pandora.Song;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Description: This is a controller for the playback of songs.
 * 	All public methods (except run() of course) can be called and expected to
 *  have a near instantaneous return and hence are safe to run on the main 
 *  thread of execution.
 * @author Dylan Powers <dylan.kyle.powers@gmail.com>
 */
public class MediaPlaybackController implements Runnable{	
	
	/* 
	 * Public 
	 */
	
	public static final int HALT_STATE_NO_NETWORK = 0;
	
	//For when Pandora's servers can't be contacted (usually the result of 
	//having no WAN connectivity)
	public static final int HALT_STATE_NO_INTERNET = 1;
	
	//A song is in the process of being prepared.
	public static final int HALT_STATE_PREPARING = 2; 	
	public static final int HALT_STATE_BUFFERING = 3;
	
	//There are no songs available in the queue to play. There are multitudes
	//of reasons this could happen, but the point is that it's causing a halt.
	public static final int HALT_STATE_NO_SONGS = 4;
	
	/**
	 * @param station_token -The token to the station to play from.
	 * @param min_quality -The minimum quality to play.
	 * @param max_quality -The maximum quality to play.
	 * @param pandora_remote -The authorized PandoraRadio RPC instance to use.
	 * @param net_connectivity -The connectivity manager so the network state 
	 * 	can be acquired.
	 * @throws Exception when an invalid audio quality range is given.
	 */
	public MediaPlaybackController(String station_token, 
			                       String min_quality,
			                       String max_quality,
			                       PandoraRadio pandora_remote,
			                       ConnectivityManager net_connectivity) throws Exception{		
		
		m_pandora_remote = pandora_remote;
		m_net_conn = net_connectivity;
		m_station_token = station_token;
 		setAudioQuality(min_quality, max_quality);
	}
	
	/**
	 * Description: This is what the thread calls when started. It is the engine
	 * 	behind the media playback controller.
	 */
	public void run(){
		
		sendPlaybackHaltedNotification(HALT_STATE_PREPARING);
		
		NetworkInfo active_network_info;
		
		instantiateInstance();
	
		
		m_active_player = instantiateMediaPlayer();
		
		m_bandwidth = new MediaBandwidthEstimator();
		
		m_alive = true;
		m_cached_player_ready_flag = false;		
		m_need_next_song = true;
		m_play_queue = new LinkedList<Song>();
		m_reset_player_flag = false;
		
		Boolean alive = true;
		while(alive.booleanValue()){
			
			int active_song_id = m_active_player.getAudioSessionId();
			
			while (m_buffer_sample_queue.peek() != null){
				BufferSample buffer_tmp = m_buffer_sample_queue.poll();
				int bitrate = 0;
				int length = 0;
				if (buffer_tmp.m_session_id == active_song_id){
					bitrate = m_active_player.getUrl().m_bitrate;
					length = m_active_player.getDuration();
				}
				else{
					bitrate = m_cached_player.getUrl().m_bitrate;
					length = m_cached_player.getDuration();
				}
				
				if (buffer_tmp.m_percent == 100){
					if (m_bandwidth.doesIdExist(buffer_tmp.m_session_id)){
						if (buffer_tmp.m_session_id == active_song_id){	
							m_active_player.m_buffer_complete_flag = true;
						}
						else if (buffer_tmp.m_session_id == m_cached_player.getAudioSessionId()){
							m_cached_player.m_buffer_complete_flag = true;
						}
					}
				}
				
				m_bandwidth.update(buffer_tmp.m_session_id, 
						           buffer_tmp.m_percent,
						           length, 
						           bitrate, 
						           buffer_tmp.m_time_stamp);
			}
			
			//Prevent a null pointer exception in case an active network is not
			//available.
			active_network_info = m_net_conn.getActiveNetworkInfo();
			if (active_network_info != null && active_network_info.isConnected()){
				if (m_playback_halted_reason == HALT_STATE_NO_NETWORK){
					m_playback_halted_reason = -1;
					sendPlaybackHaltedNotification(HALT_STATE_BUFFERING);
				}
				
				if (isPlayQueueLow()){
					pushMoreSongs();
				}
				
				if (m_need_next_song){
					prepareNextSong();
				}
				else if (m_reset_player_flag){					
					m_bandwidth.reset();
					rebufferSong(getOptimizedPandoraAudioUrl(m_active_player.getSong()));				
				}
				else if (m_active_player.isBuffering()){
					adjustAudioQuality();
				}
				else if (m_active_player.m_buffer_complete_flag 
							&& !m_cached_player_ready_flag
							&& !m_pause){
					prepCachedPlayer();
				}				
			}
			else {
				m_bandwidth.reset();
				if (m_playback_halted_reason > HALT_STATE_NO_NETWORK){
					sendPlaybackHaltedNotification(HALT_STATE_NO_NETWORK);
				}
			}
			
			if (!m_active_player.isPlaying() && !m_pause && m_valid_play_command_flag){
				m_active_player.start();
			}
			
			try {	
				//Sleep for 1 second
				alive = m_stop_exchanger.exchange(alive, 1, TimeUnit.SECONDS); 
			} 
			catch (InterruptedException e) {} //We don't care
			catch (TimeoutException e) {} //Yes we do want this to happen
		}
		
		//Cleanup!
		m_valid_play_command_flag = false;
		m_active_player.release();
		m_play_queue.clear();
		if (m_cached_player_ready_flag){
			m_cached_player.release();
		}
	}
	
	/**
	 * Description: Gets the quality string of the audio currently playing.
	 * @return A string relating to the constant that specifies the audio
	 * 	quality.
	 * @throws Exception when the playback engine is not alive.
	 */
	public String getCurrentQuality() throws Exception{
		if (isAlive()){
			return m_active_player.getUrl().m_type;
		}
		throw new Exception("No quality available. The playback engine is not alive.");
	}
	
	/**
	 * Description: Does what it says and gets the song that's currently playing.
	 * @return The currently playing song.
	 * @throws Exception when the playback engine is not alive.
	 */
	public Song getSong() throws Exception{
		if (isAlive()){
			return m_active_player.getSong(); 
		}
		throw new Exception("No song available. The playback engine is not alive.");
	}
	
	/**
	 * Description: Checks if the playback controller is alive, or in other 
	 *  terms if the thread has been started and the stop command hasn't been 
	 *  called. The majority of commands only have significance when this is 
	 *  true.
	 * @return
	 */
	public boolean isAlive(){
		return m_alive.booleanValue();
	}
	
	/**
	 * Description: Starts/resumes media playback.
	 */
	public void play(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				if (m_valid_play_command_flag){
					m_active_player.start();
				}
				m_pause = false;
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
					if (m_active_player.isPlaying()){
						m_active_player.pause();
					}					
				}
				m_pause = true;
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
	 */
	public void setOnErrorListener(OnErrorListener listener){
		m_error_listener = listener;
	}
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when a new song is played.
	 * @param listener -Implements OnNewSongListener
	 */
	public void setOnNewSongListener(OnNewSongListener listener){
		m_new_song_listener = listener;
	}
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when playback continues after it has been halted.
	 * @param listener -Implements OnPlaybackContinued listener
	 */
	public void setOnPlaybackContinuedListener(OnPlaybackContinuedListener listener){
		m_playback_continued_listener = listener;
	}
	
	/**
	 * Description: This sets a listener for a method to occur on the main
	 *  thread of execution when playback has been halted due to network
	 *  conditions.
	 * @param listener -Implements OnPlaybackHaltedListener
	 */
	public void setOnPlaybackHaltedListener(OnPlaybackHaltedListener listener){
		m_playback_halted_listener = listener;
	}
	
	/**
	 * Description: Skips to the next song.
	 */
	public void skip(){
		Thread t = new Thread(new Runnable(){
			public void run(){
				if (isAlive()){			
					m_need_next_song = true;
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
	private final Object quality_lock = new Object();
	private final Object send_playback_notification_lock = new Object();
	
	//Listeners
	private volatile OnErrorListener m_error_listener;
	private volatile OnNewSongListener m_new_song_listener;
	private volatile OnPlaybackContinuedListener m_playback_continued_listener;
	private volatile OnPlaybackHaltedListener m_playback_halted_listener;	

	//Players
	private ConcurrentSongMediaPlayer m_active_player;
	private ConcurrentSongMediaPlayer m_cached_player;
	
	//Our thread safe queue for the buffer samples
	private final ConcurrentLinkedQueue<BufferSample> 
		m_buffer_sample_queue = new ConcurrentLinkedQueue<BufferSample>();
	
	//Other variables required for the controller to run.
	private volatile Boolean m_alive = false;
	private MediaBandwidthEstimator m_bandwidth;
	private volatile Boolean m_cached_player_ready_flag;
	private String m_min_quality;
	private String m_max_quality;
	private volatile Boolean m_need_next_song;
	private ConnectivityManager m_net_conn;
	private PandoraRadio m_pandora_remote;
	private volatile Boolean m_pause = false;
	private LinkedList<Song> m_play_queue;
	private volatile Thread m_playback_engine_thread;
	private int m_playback_halted_reason = -1;
	private volatile Boolean m_reset_player_flag;
	private volatile Boolean m_restart_song_flag;
	private String m_station_token;
	private Exchanger<Boolean> m_stop_exchanger = new Exchanger<Boolean>();
	private volatile Boolean m_valid_play_command_flag = false;
	
	/**
	 * Description: Adjusts the audio quality of a playing song. It will only 
	 * 	move the quality down. This is for when buffering occurs.
	 */
	private void adjustAudioQuality(){
		PandoraAudioUrl 
			best_available_quality = getOptimizedPandoraAudioUrl(m_active_player.getSong());
		try {
			
			//If the new quality is lower than the one we already have (there's no
			//sense in reinitializing a player to the same audio quality that it
			//already had, and we don't want to automatically lower the quality
			//in unnecessary circumstances such as directly after a player has
			//been prepared) or if the download has essentially stalled out 
			//(the optimized url generator will default to the max quality in
			//such instances when the bitrate is 0 or unknown).
			if (PandoraRadio.audioQualityCompare(best_available_quality.m_type, 
					                             m_active_player.getUrl().m_type) < 0
		                             ||
                m_bandwidth.getBitrate() == 0){
				rebufferSong(best_available_quality);
			}
		} catch (Exception e) {
			Log.e("Pandoroid", e.getMessage(), e);
		}
		
	}
	
	/**
	 * Description: Gets the url to the highest bitrate of audio available 
	 * 	dependent on current network conditions. If network conditions cannot 
	 * 	be properly evaluated, it will default to the max available audio 
	 *  quality.
	 * @param song -The song to get the url for.
	 * @return A PandoraAudioUrl that holds the url.
	 */
	private PandoraAudioUrl getOptimizedPandoraAudioUrl(Song song){
		int bitrate = 0;
		PandoraAudioUrl url = null;
		

		bitrate = m_bandwidth.getBitrate();
			
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
		return m_error_listener;
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
		return m_new_song_listener;
	}
	
	/**
	 * Description: Thread safe accessor for m_playback_continued_listener.
	 * @return The playback continued listener.
	 */
	private OnPlaybackContinuedListener getPlaybackContinuedListener(){
		return m_playback_continued_listener;
	}
	
	
	
	/**
	 * Description: Thread safe accessor for m_playback_halted_listener.
	 * @return The playback halted listener.
	 */
	private OnPlaybackHaltedListener getPlaybackHaltedListener(){
		return m_playback_halted_listener;
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
	private ConcurrentSongMediaPlayer instantiateMediaPlayer(){
		ConcurrentSongMediaPlayer media_player = new ConcurrentSongMediaPlayer();
		media_player.setOnCompletionListener(new MediaCompletionListener());
		media_player.setOnBufferingUpdateListener(new MediaBufferingUpdateListener());
		//media_player.setOnErrorListener(new MediaErrorListener());
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
		return (PandoraRadio.audioQualityCompare(max_quality, min_quality) >= 0);
	}
	
	/**
	 * Description: Prepares a song so it can be played by the media player.
	 */
	private void prepareNextSong(){
		m_valid_play_command_flag = false;
		if (m_play_queue.peek() != null){

			m_active_player.setSong(m_play_queue.pollFirst());
			sendNewSongNotification(m_active_player.getSong());
			if (m_cached_player_ready_flag){
				m_active_player.copy(m_cached_player);
				m_valid_play_command_flag = true;
				m_cached_player_ready_flag = false;
				Log.i("Pandoroid", 
					  "Current Audio Quality: " + m_active_player.getUrl().m_bitrate);
			}
			else{
				try {
					sendPlaybackHaltedNotification(HALT_STATE_PREPARING);
					PandoraAudioUrl new_url = getOptimizedPandoraAudioUrl(m_active_player.getSong());
					Log.i("Pandoroid", "Current Audio Quality: " + new_url.m_bitrate);
					m_active_player.prepare(new_url);
					m_valid_play_command_flag = true;
					m_need_next_song = false;
					sendPlaybackContinuedNotification();
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
					sendPlaybackHaltedNotification(HALT_STATE_NO_INTERNET);
					m_reset_player_flag = true;
					m_need_next_song = false;
					Log.e("Pandoroid", e.getMessage(), e);
				}
			}
		}
		else{
			sendPlaybackHaltedNotification(HALT_STATE_NO_SONGS);
		}
	}
	
	/**
	 * Description: Preps the cached player so it will be ready later. 
	 */
	private void prepCachedPlayer(){		
		if (m_play_queue.peek() != null){
			m_cached_player = instantiateMediaPlayer();
			m_cached_player.setSong(m_play_queue.peek());
			try {
				m_cached_player.prepare(getOptimizedPandoraAudioUrl(m_cached_player.getSong()));
				m_cached_player_ready_flag = true;
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
		catch (RPCException e){
			sendErrorNotification(e.getMessage(), e, true, e.code);
		}
		catch (Exception e){
			Log.e("Pandoroid", e.getMessage(), e);
		}
	}
	
	/**
	 * Description: Takes the current playing song and prepares it for a new
	 * 	url.
	 * @param url -A PandoraAudioUrl for the audio url that needs to be played.
	 */
	private void rebufferSong(PandoraAudioUrl url){
		m_valid_play_command_flag = false;
		
		//A release on the cached player can in fact affect the 
		//active player.
		if (m_cached_player != null && (m_cached_player.getPlayer() != m_active_player.getPlayer())){
			m_cached_player_ready_flag = false;
			m_cached_player.release();
		}
		
		try {
			m_active_player.prepare(url);
			Log.i("Pandoroid", "Current Audio Quality: " + url.m_bitrate);
			m_valid_play_command_flag = true;
			m_reset_player_flag = false;
			sendPlaybackContinuedNotification();
		} 
		catch (IllegalArgumentException e) {
			Log.e("Pandoroid", e.getMessage(), e);
			m_need_next_song = true;
		}
		catch (SecurityException e) {
			Log.e("Pandoroid", e.getMessage(), e);
			m_need_next_song = true;
		} 
		catch (IllegalStateException e) {
			Log.e("Pandoroid", e.getMessage(), e);
			m_need_next_song = true;
		}
		catch (IOException e) {
			sendPlaybackHaltedNotification(HALT_STATE_NO_INTERNET);
			m_reset_player_flag = true;
			Log.e("Pandoroid", e.getMessage(), e);
		}
	}
	
	/**
	 * Description: Sends an error notification to the main thread.
	 * @param error_message -A string with the error message.
	 * @param e -The error.
	 * @param remote_error_flag -Whether or not this is an error from the 
	 * 	remote server.
	 * @param rpc_error_code -If this is a remote server error, then this is
	 * 	the Pandora RPC error affiliated with it. 
	 */
	private void sendErrorNotification(String error_message,
									   Throwable e,
									   boolean remote_error_flag,
									   int rpc_error_code){
		Log.e("Pandoroid", error_message, e);
		Handler handler = new Handler(Looper.getMainLooper());
		SendErrorNotificationTask 
			task = new SendErrorNotificationTask(error_message, e,
											     remote_error_flag,
											     rpc_error_code);
		handler.post(task);
	}
	
	/**
	 * Description: Notifies the main thread about a new song.
	 * @param song -The new song being played.
	 */
	private void sendNewSongNotification(Song song){
		Log.i("Pandoroid", "New song: " + song.getTitle());
		Handler handler = new Handler(Looper.getMainLooper());
		SendNewSongNotificationTask task = new SendNewSongNotificationTask(song);
		handler.post(task);
	}
	
	/**
	 * Description: Notifies the main thread that playback has stopped, and why
	 * 	that is so.
	 * @param halt_code -The halt code describing why playback has stopped.
	 */
	private void sendPlaybackHaltedNotification(int halt_code){
		synchronized(send_playback_notification_lock){
			if (m_playback_halted_reason == -1 || m_playback_halted_reason > halt_code){
				m_playback_halted_reason = halt_code;
				Log.i("Pandoroid", "Playback halted: " + halt_code);
				Handler handler = new Handler(Looper.getMainLooper());
				SendPlaybackHaltedNotificationTask 
					task = new SendPlaybackHaltedNotificationTask(halt_code);
				handler.post(task);
			}	
		}
	}
	
	/**
	 * Description: Notifies the main thread that playback has now continued
	 * 	from the previous halt state. As a side effect, it resets the playback 
	 *  halted reason to -1.
	 */
	private void sendPlaybackContinuedNotification(){
		synchronized(send_playback_notification_lock){
			Log.i("Pandoroid", "Playback continued");
			Handler handler = new Handler(Looper.getMainLooper());
			SendPlaybackContinuedNotificationTask
				task = new SendPlaybackContinuedNotificationTask();
			handler.post(task);
			m_playback_halted_reason = -1;
		}
	}
	

	/**
	 * Description: Does the job of stopping the playback controller. It will
	 *  not return until the playback is stopped, but that doesn't necessarily
	 *  mean the controller thread has ended.
	 */
	private void stopTask(){
		if (isAlive()){
			m_alive = false;
			try {					
				m_stop_exchanger.exchange(false);
			} catch (InterruptedException e) {}
		}
	}
	
	/**
	 * Description: Stores a buffer position with the time it was created, and
	 * 	a MediaPlayer session id it's affiliated with.
	 */
	private class BufferSample{
		public int m_percent;
		public int m_session_id;
		public long m_time_stamp;
		
		public BufferSample(int session_id, int percent){
			this.m_percent = percent;
			this.m_session_id = session_id;
			this.m_time_stamp = System.currentTimeMillis();
		}		
	}
	
	/**
	 * Description: A buffering update listener for the media players.
	 */
	private class MediaBufferingUpdateListener implements MediaPlayer.OnBufferingUpdateListener{		
		public void onBufferingUpdate(MediaPlayer mp, int percent){
			BufferSample sample = new BufferSample(mp.getAudioSessionId(), percent);
			m_buffer_sample_queue.add(sample);
		}
	}
	
	/**
	 * Description: Implements the OnCompletionListener for the media player.
	 */
	private class MediaCompletionListener implements MediaPlayer.OnCompletionListener{
		public void onCompletion(MediaPlayer mp){
			Thread t = new Thread(new Runnable(){
				public void run(){
					if (m_active_player.isPlaybackComplete()){
						m_need_next_song = true;
					}
					else{
						sendPlaybackHaltedNotification(HALT_STATE_BUFFERING);
						m_reset_player_flag = true;
					}
					m_playback_engine_thread.interrupt();
				}
			});
			t.start();
		}
	}
	
	/**
	 * Description: An info listener for the media players. It primarily looks
	 * 	for when buffering has started, and ended.
	 */
	private class MediaInfoListener implements MediaPlayer.OnInfoListener{
		public boolean onInfo(MediaPlayer mp, int what, int extra){
			
			if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START){
				sendPlaybackHaltedNotification(HALT_STATE_BUFFERING);
				m_active_player.setBuffering(true);
			}
			else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END){
				sendPlaybackContinuedNotification();
				m_active_player.setBuffering(false);
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
	
	/**
	 * Description: The task to be run when an error notification needs to be
	 * 	sent.
	 */
	private class SendErrorNotificationTask implements Runnable{
		public SendErrorNotificationTask(String message, Throwable e,
										 boolean remote_error_flag,
										 int rpc_error_code){
			this.m_message = message;
			this.m_e = e;
			this.m_remote_error_flag = remote_error_flag;
			this.m_rpc_error_code = rpc_error_code;
		}
		
		public void run(){
			OnErrorListener listener = getErrorListener();
			if (listener != null){
				listener.onError(this.m_message, this.m_e, 
						         this.m_remote_error_flag, 
						         this.m_rpc_error_code);
			}
		}
		
		private String m_message;
		private Throwable m_e;
		private Boolean m_remote_error_flag;
		private int m_rpc_error_code;
	}
	
	/**
	 * Description: The task to be run when a new song notification needs to be
	 * 	sent.
	 */
	private class SendNewSongNotificationTask implements Runnable{
		public SendNewSongNotificationTask(Song song){
			this.m_song = song;
		}
		
		public void run(){
			OnNewSongListener listener = getNewSongListener();
			if (listener != null){
				listener.onNewSong(this.m_song);
			}
		}
		
		private Song m_song;
	}
	
	/**
	 * Description: The task to be run when a notification alerting to the
	 * 	playback being halted needs to be sent.
	 */
	private class SendPlaybackHaltedNotificationTask implements Runnable{
		public SendPlaybackHaltedNotificationTask(int halt_code){
			this.m_code = halt_code;
		}
		
		public void run(){
			OnPlaybackHaltedListener listener = getPlaybackHaltedListener();
			if (listener != null){
				listener.onPlaybackHalted(this.m_code);
			}
		}
		
		private int m_code;
	}
	
	/**
	 * Description: The task to be run when a notification alerting to the
	 * 	playback being continued from a halt state needs to be sent.
	 */
	private class SendPlaybackContinuedNotificationTask implements Runnable{
		public void run(){
			OnPlaybackContinuedListener listener = getPlaybackContinuedListener();
			if (listener != null){
				listener.onPlaybackContinued();
			}
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
