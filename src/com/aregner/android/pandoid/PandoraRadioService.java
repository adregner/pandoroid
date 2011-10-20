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
package com.aregner.android.pandoid;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.aregner.pandora.PandoraRadio;
import com.aregner.pandora.SearchResult;
import com.aregner.pandora.Song;
import com.aregner.pandora.Station;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PandoraRadioService extends Service {

	private static final int NOTIFICATION_SONG_PLAYING = 1;
	
	// tools this service uses
	private PandoraRadio pandora;
	private MediaPlayer media;
	private AudioFocusListener focusListener;
	
	private NotificationManager notificationManager;
	private AudioManager audioManager;
	private TelephonyManager telephonyManager;
	private SharedPreferences prefs;
	
	// tracking/organizing what we are doing
	private Station currentStation;
	private Song[] currentPlaylist;
	private Song[] nextPlaylist;
	private List<SearchResult> searchResults;
	private int currentSongIndex;
	
	public static final String SONG_CHANGE = "com.aregner.android.pandoid.PanoraRadioService.SONG_CHANGE";

	protected PandoraDB db;

	private static final String LOG_TAG = "PandoraRadioService";

		
	// static usefullness
	private static PandoraRadioService instance;
	private static Object lock = new Object();

	public static void createPandoraRadioService(Context context) {
		synchronized(lock) {
			if(instance == null) {
				context.startService(new Intent(context, PandoraRadioService.class));
			}
		}
	}
	public static PandoraRadioService getInstance(boolean wait) {
		if(wait) {
			long startedWaiting = System.currentTimeMillis();
			while( instance == null && System.currentTimeMillis() - startedWaiting < 5000L ) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
		synchronized(lock) {
			return instance;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		synchronized(lock) {
			super.onCreate();
			instance = this;
			pandora = new PandoraRadio();
			media = new MediaPlayer();
			
			audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
			notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			
			focusListener = new AudioFocusListener();

			// Register the listener with the telephony manager
			telephonyManager.listen(new PhoneStateListener() {
				boolean pausedForRing = false;
				@Override
				public void onCallStateChanged(int state, String incomingNumber) {
					switch(state) {

					case TelephonyManager.CALL_STATE_IDLE:
						if(pausedForRing && !media.isPlaying()) {
							if(prefs.getBoolean("behave_resumeOnHangup", true)) {
								media.start();
								setNotification();
								pausedForRing = false;
							}
						}
						break;

					case TelephonyManager.CALL_STATE_OFFHOOK:
					case TelephonyManager.CALL_STATE_RINGING:
						if(media.isPlaying()) {
							// pausing it this way keeps the notification up there
							media.pause();
							pausedForRing = true;
						}
						break;
					}
				}
			}, PhoneStateListener.LISTEN_CALL_STATE);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		//return START_REDELIVER_INTENT;
		return START_STICKY;
	}
	
	public void setNotification() {
		Notification notification = new Notification(R.drawable.icon, "Pandoroid Radio", System.currentTimeMillis());
		Intent notificationIntent = new Intent(this, PandoidPlayer.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, NOTIFICATION_SONG_PLAYING, notificationIntent, 0);
		
		notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;
		notification.setLatestEventInfo(getApplicationContext(), "Pandoroid Radio", "Playing "+getCurrentSong().getTitle(), contentIntent);
		//notificationManager.notify(NOTIFICATION_SONG_PLAYING, notification);
		startForeground(NOTIFICATION_SONG_PLAYING, notification);
	}

	/**  */
	private class PrepareNextPlaylistTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected Void doInBackground(Void... arg0) {
			nextPlaylist = currentStation.getPlaylist( prefs.getString("pandora_audioFormat", PandoraRadio.DEFAULT_AUDIO_FORMAT) );
			return null;
		}
	}

	/** methods for clients */
	public void signIn(String username, String password) {
		pandora.connect(username, password);
	}
	public void signOut() {
		if(media != null) {
			if(isPlaying())
				media.stop();
			media.release();
			media = null;
		}

		if(pandora != null) {
			pandora.disconnect();
			pandora = null;
		}

		instance = null;
		stopSelf();
	}
	public boolean isAlive() {
		return pandora.isAlive();
	}
	public ArrayList<Station> getStations(boolean forceDownload) {
		if(forceDownload) {
			return getStations();
		}
		else {
			// TODO : build a "fake" list of stations that the PandoraRadio controller doesn't know about???
			HashMap<String, Object>[] stationsData = db.getStations();
			ArrayList<Station> stations = new ArrayList<Station>(stationsData.length);
			
			for(int s=0; s<stationsData.length; s++) {
				stations.add(new Station(stationsData[s], pandora));
			}
			
			return stations;
		}
	}
	public ArrayList<Station> getStations() {
		ArrayList<Station> stations;

		stations = pandora.getStations();
		
		if(stations == null){
			reauthenticate();
		}

		db = new PandoraDB(getBaseContext());
		db.syncStations(stations);
		db.close();
		
		return stations;
	}
	private void reauthenticate() {
		
		String username = prefs.getString("pandora_username", null);
		String password = prefs.getString("pandora_password", null);
		
		if(username != null && password != null){
			Log.i(LOG_TAG , "Reauthenticating...");
			pandora.disconnect();
			signIn(username,password);
		}
		if(pandora.isAlive()){
			Log.i(LOG_TAG, "Success!");
		}
		else {
			Log.i(LOG_TAG, "Failed!");
		}
		
		
	};
	public void setCurrentStationId(long sid) {
		if(sid < 0) return;
		currentStation = pandora.getStationById(sid);
	}
	public Station getCurrentStation() {
		return currentStation;
	}
	public Song getCurrentSong() {
		return currentPlaylist[currentSongIndex];
	}
	public boolean isPlayable() {
		return currentStation != null && pandora.isAlive();
	}
	public boolean isPlaying() {
		return media.isPlaying();
	}
	public boolean isReadytoUpdateUI() {
		boolean ready = false;
		
		if (instance != null && media != null && currentStation != null && currentPlaylist != null) {
			ready = true;
		}
		return ready;
	}
	public void prepare() {
		currentPlaylist = currentStation.getPlaylist( prefs.getString("pandora_audioFormat", PandoraRadio.DEFAULT_AUDIO_FORMAT) );
		prepare(0);
	}
	public void prepare(int i) {
		currentSongIndex = i;
		media.reset();
		
		media.setOnCompletionListener(new OnCompletionListener(){
			@Override
			public void onCompletion(MediaPlayer mp) {
				next();
			}
		});
	
	//	media.setOnCompletionListener((OnCompletionListener)listeners.get(OnCompletionListener.class));
	//	media.setOnPreparedListener((OnPreparedListener)listeners.get(OnPreparedListener.class));
		try {
			media.setDataSource( currentPlaylist[i].getAudioUrl() );
		} catch (IllegalArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IllegalStateException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} 
		try {
			media.prepare();
		} catch (IllegalStateException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	public Song play() {
		return play(0);
	}
	public Song play(int i) {
		audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC , AudioManager.AUDIOFOCUS_GAIN);
		
		media.start();
		setNotification();
		songChangeEvent();
		
		return currentPlaylist[i];
	}
	public void pause() {
		if(media.isPlaying()) {
			audioManager.abandonAudioFocus(focusListener);
			media.pause();
			stopForeground(true);
		}
		else {
			audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC , AudioManager.AUDIOFOCUS_GAIN);
			media.start();
			setNotification();
		}
	}
	public Song next() {
		// play the next song in the current list
		if(++currentSongIndex < currentPlaylist.length) {
			prepare(currentSongIndex);

			// prepare the next playlist if we are nearing the end
			if(currentSongIndex + 1 >= currentPlaylist.length) {
				(new PrepareNextPlaylistTask()).execute();
			}

			return play(currentSongIndex);
		}
		// switch to a pre-fetched playlist for the next one
		else if(nextPlaylist != null) {
			currentPlaylist = nextPlaylist;
			nextPlaylist = null;
			prepare(0);
			return play();
		}
		// we don't have anything
		else {
			// get a new playlist
			prepare();
			return play();
		}
	}
	public void rate(String rating) {
		if(rating == PandoidPlayer.RATING_NONE) {
			// cannot set rating to none
			return;
		}
		
		boolean ratingBool = rating.equals(PandoidPlayer.RATING_LOVE) ? true : false;
		
		pandora.rate(currentStation, currentPlaylist[currentSongIndex], ratingBool);
	}
	public void search(String query, String type){
		int queryType;
		if(type.equals("Artist")){
			queryType = pandora.ARTIST_QUERY;
		}
		else{
			queryType = pandora.TRACK_QUERY;
		}
		searchResults =  pandora.search(query, queryType);
	}
	public List<SearchResult> getSearchResults(){
		return searchResults;
	}
	private void songChangeEvent() {
		Intent i = new Intent();
		i.setAction(SONG_CHANGE);
		sendBroadcast(i);	
	} 
	private class AudioFocusListener implements OnAudioFocusChangeListener {

		@Override
		public void onAudioFocusChange(int focusChange) {
			if(focusChange == AudioManager.AUDIOFOCUS_GAIN) {
				audioManager.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.ADJUST_RAISE);
			}
			else if(focusChange == AudioManager.AUDIOFOCUS_LOSS){
				media.pause();
				stopForeground(true);
			}
			else if(focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
				media.pause();
			}
			else if(focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK){
				audioManager.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.ADJUST_LOWER);
			}
		}
	}

	public Station createStation(String musicId) {
		return pandora.createStation(musicId, pandora.TYPE_MUSIC_ID);	
	}
	public void deleteStation(Station station) {
		String stationId = station.getStationId();
		pandora.deleteStation(stationId);		
	}

}
