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
package com.pandoroid.android;

import java.util.ArrayList;
import java.util.HashMap;

import com.pandoroid.pandora.PandoraRadio;
import com.pandoroid.pandora.RPCException;
import com.pandoroid.pandora.Song;
import com.pandoroid.pandora.Station;
import com.pandoroid.pandora.SubscriberTypeException;
import com.pandoroid.playback.MediaPlaybackController;
import com.pandoroid.playback.OnNewSongListener;
import com.pandoroid.android.R;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

/**
 * Description: Someone really needs to give this class some loving, document
 *  it up, organize it, and make it thread safe.
 */
public class PandoraRadioService extends Service {

	private static final int NOTIFICATION_SONG_PLAYING = 1;
	

    
	// tools this service uses
	private PandoraRadio pandora;
	public MediaPlaybackController song_playback;
	public ImageDownloader image_downloader;
	
	private TelephonyManager telephonyManager;
	private ConnectivityManager connectivity_manager;
	private SharedPreferences prefs;
	
	// tracking/organizing what we are doing
	private Station currentStation;
	private String audio_quality;
	private boolean paused;
	private HashMap<Class<?>,Object> listeners = new HashMap<Class<?>,Object>();

	protected PandoraDB db;

	
	// static usefullness
	private static Object lock = new Object();
	private static Object pandora_lock = new Object();

//	public static void createPandoraRadioService(Context context) {
//		synchronized(lock) {
//			if(instance == null) {
//				context.startService(new Intent(context, PandoraRadioService.class));
//			}
//		}
//	}
	
//	public static PandoraRadioService getInstance(boolean wait) {
//		if(wait) {
//			long startedWaiting = System.currentTimeMillis();
//			while( instance == null && System.currentTimeMillis() - startedWaiting < 5000L ) {
//				try {
//					Thread.sleep(50);
//				} catch (InterruptedException e) {
//					Log.e("Pandoroid", "RadioService exception Sleeping", e);
//				}
//			}
//		}
//		
//		synchronized(lock) {
//			return instance;
//		}
//	}

	//Taken straight from the Android service reference
    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class PandoraRadioBinder extends Binder {
		PandoraRadioService getService() {
            return PandoraRadioService.this;
        }
    }
    
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

    // This is the object that receives interactions from clients. 
    private final IBinder mBinder = new PandoraRadioBinder();
    //End service reference
    
    
	@Override
	public void onCreate() {
		//synchronized(lock) {
			//super.onCreate();
			
			paused = false;
			pandora = new PandoraRadio();
			(new PandoraDeviceLoginTask()).execute(Boolean.valueOf(false));
			image_downloader = new ImageDownloader();
			
			
			connectivity_manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
			telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

			// Register the listener with the telephony manager
			telephonyManager.listen(new PhoneStateListener() {
				boolean pausedForRing = false;
				@Override
				public void onCallStateChanged(int state, String incomingNumber) {
					switch(state) {

					case TelephonyManager.CALL_STATE_IDLE:
						if(pausedForRing && song_playback != null) {
							if(prefs.getBoolean("behave_resumeOnHangup", true)) {
								play();
							}
						}
						
						pausedForRing = false;
						break;

					case TelephonyManager.CALL_STATE_OFFHOOK:
					case TelephonyManager.CALL_STATE_RINGING:
						if(song_playback != null) {
							song_playback.pause();
						}					

						pausedForRing = true;						
						break;
					}
				}
			}, PhoneStateListener.LISTEN_CALL_STATE);
		//}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	public void onDestroy() {
		if (song_playback != null){
			song_playback.stop();
		}
		stopForeground(true);
		return;
	}
	
	public void setListener(Class<?> klass, Object listener) {
		listeners.put(klass, listener);
	}	
	
	public void setNotification() {
		Notification notification = new Notification(R.drawable.notification_icon, 
				                                     "Pandoroid Radio", 
				                                     System.currentTimeMillis());
		Intent notificationIntent = new Intent(this, PandoroidPlayer.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 
				                                                NOTIFICATION_SONG_PLAYING, 
				                                                notificationIntent, 
				                                                0);
		
		notification.flags |= Notification.FLAG_ONGOING_EVENT | Notification.FLAG_FOREGROUND_SERVICE;
		Song tmp_song = song_playback.getSong();
		notification.setLatestEventInfo(getApplicationContext(), tmp_song.getTitle(),
				tmp_song.getArtist()+" on "+tmp_song.getAlbum(), contentIntent);
		startForeground(NOTIFICATION_SONG_PLAYING, notification);
	}


	/** methods for clients */
	public boolean signIn(String username, String password) {
		boolean toRet = false;
		boolean needs_partner_login = false;
		boolean is_pandora_one_user = false;
		int attempts = 3;
		
		//Low connectivity could cause reattempts to be need to be made
		while (attempts > 0){
			try{
				if (needs_partner_login){
					synchronized(pandora_lock){
						pandora.runPartnerLogin(is_pandora_one_user);
					}
					needs_partner_login = false;
				}
				if (is_pandora_one_user){
					audio_quality = PandoraRadio.MP3_192;
				}
				else {
					audio_quality = PandoraRadio.MP3_128;
				}
				synchronized(pandora_lock){
					toRet = pandora.connect(username, password);
				}
				attempts = 0;
			}
			catch (SubscriberTypeException e){
				needs_partner_login = true;
				is_pandora_one_user = e.is_pandora_one;
				Log.i("Pandoroid", "Wrong subscriber type. Running partner login");
			}
			catch (RPCException e){
				if (e.code == 13){
					--attempts;
				}
				else {
					Log.e("Pandroroid","Exception logging in", e);
					toRet = false;
					attempts = 0;
				}
			}
			catch (Exception e){
				Log.e("Pandroroid","Exception logging in", e);
				toRet = false;
				attempts = 0;
			}
		}		
		return toRet;
	}
	
	public void signOut() {
		if(song_playback != null) {
			song_playback.stop();
		}

		if(pandora != null) {
			pandora.disconnect();
			pandora = null;
		}
		
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
			HashMap<String, Object>[] stationsData = db.getStations();
			ArrayList<Station> stations = new ArrayList<Station>(stationsData.length);
			
			for(int s=0; s<stationsData.length; s++) {
				stations.add(new Station(stationsData[s], pandora));
			}
			
			return stations;
		}
	}
	@SuppressWarnings("unchecked")
	public ArrayList<Station> getStations() {
		ArrayList<Station> stations = null;
		
		try{
			stations = pandora.getStations();

			(new AsyncTask<ArrayList<Station>, Void, Void>() {
				@Override
				protected Void doInBackground(ArrayList<Station>... params) {
					db = new PandoraDB(getBaseContext());
					db.syncStations(params[0]);
					db.close();
					return null;
				}
			}).execute(stations);
	
		}
		catch (Exception e){
			Log.e("Pandoroid", "Exception fetching stations: ", e);
		}

		return stations;
	}
	public boolean setCurrentStationId(String sid) {
		if(sid.compareTo("") == 0) return false;
		currentStation = pandora.getStationById(sid);
		setPlaybackController();
		return currentStation != null;
	}
	
	public Station getCurrentStation() {
		return currentStation;
	}
	
	public Song playPause(){
		if (song_playback != null){
			if (!paused){
				pause();
				return song_playback.getSong();
			}
			else{
				return play();
			}
		}
		return null;
	}

	private Song play() {
		song_playback.play();
		setNotification();
		paused = false;
		return song_playback.getSong();
	}
	
	private void pause() {
		song_playback.pause();			
		paused = true;
		stopForeground(true);
	}
	
	

	
	public void rate(String rating) {
		if(rating == PandoroidPlayer.RATING_NONE) {
			// cannot set rating to none
			return;
		}
		
		boolean ratingBool = rating.equals(PandoroidPlayer.RATING_LOVE) ? true : false;
		try{
			pandora.rate(song_playback.getSong(), ratingBool);
		}
		catch(Exception e){
			Log.e("Pandoroid", "Exception sending a song rating", e);
		}
	}
	
	public void resetPlaybackListeners(){
		if (song_playback != null){
			try {
				song_playback.setOnNewSongListener(
						(OnNewSongListener) listeners.get(OnNewSongListener.class)
						                          );
			} 
			catch (Exception e) {
				Log.e("Pandoroid", e.getMessage(), e);
			}
		}
	}
	
	private void setPlaybackController(){
		if (currentStation != null){
			try{	
				if (song_playback == null){		
					song_playback = new MediaPlaybackController(currentStation.getStationIdToken(),
							                                    audio_quality,
							                                    audio_quality,
							                                    pandora,
							                                    connectivity_manager);

					
				}
				else{
					song_playback.reset(currentStation.getStationIdToken(), pandora);
					
				}
				resetPlaybackListeners();
			} 
			catch (Exception e) {
				Log.e("Pandoroid", e.getMessage(), e);
				song_playback = null;
			}
		}
	}
	
	public void startPlayback(){
		if (song_playback == null){
			setPlaybackController();
		}
		
		if (song_playback != null){
			Thread t = new Thread(song_playback);
			t.start();
		}
		
	}
	
	
	
	private class PandoraDeviceLoginTask extends AsyncTask<Boolean, Void, Boolean>{
		protected Boolean doInBackground(Boolean... subscriber_type){
			Boolean success_flag = false;
			try {
				synchronized(pandora_lock){
					pandora.runPartnerLogin(subscriber_type[0].booleanValue());
				}
				success_flag = true;
			}
			catch (RPCException e){
				Log.e("Pandoroid", "RPC error", e);
			}
			catch (Exception e){
				Log.e("Pandoroid", "Fatal error initializing PandoraRadio", e);
			}
			
			return success_flag;
		}
		
		protected void onPostExecute(Boolean... success){
			//Maybe we could do something with this information eventually....
		}
	}
}
