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
package com.pandoroid;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.HttpStatus;
import org.apache.http.client.HttpResponseException;

import com.pandoroid.pandora.PandoraRadio;
import com.pandoroid.pandora.RPCException;
import com.pandoroid.pandora.Song;
import com.pandoroid.pandora.Station;
import com.pandoroid.pandora.SubscriberTypeException;
import com.pandoroid.playback.MediaPlaybackController;
import com.pandoroid.playback.OnNewSongListener;
import com.pandoroid.playback.OnPlaybackContinuedListener;
import com.pandoroid.playback.OnPlaybackHaltedListener;
import com.pandoroid.R;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.Uri;
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
	private PandoraRadio m_pandora_remote;
	public MediaPlaybackController m_song_playback;
	public ImageDownloader image_downloader;
	
	private TelephonyManager telephonyManager;
	private ConnectivityManager connectivity_manager;
	private SharedPreferences m_prefs;
	
	// tracking/organizing what we are doing
	private Station m_current_station;
	private String m_audio_quality;
	private boolean m_paused;
	
	//We'll use this for now as the database implementation is garbage.
	private ArrayList<Station> m_stations; 
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
		m_paused = false;
		m_pandora_remote = new PandoraRadio();
		image_downloader = new ImageDownloader();
		m_stations = new ArrayList<Station>();
		
		
		connectivity_manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		// Register the listener with the telephony manager
		telephonyManager.listen(new PhoneStateListener() {
			boolean pausedForRing = false;
			@Override
			public void onCallStateChanged(int state, String incomingNumber) {
				switch(state) {

				case TelephonyManager.CALL_STATE_IDLE:
					if(pausedForRing && m_song_playback != null) {
						if(m_prefs.getBoolean("behave_resumeOnHangup", true)) {
							if(m_song_playback != null && !m_paused){
								m_song_playback.play();
							}
						}
					}
					
					pausedForRing = false;
					break;

				case TelephonyManager.CALL_STATE_OFFHOOK:
				case TelephonyManager.CALL_STATE_RINGING:
					if(m_song_playback != null) {
						m_song_playback.pause();
					}					

					pausedForRing = true;						
					break;
				}
			}
		}, PhoneStateListener.LISTEN_CALL_STATE);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}
	
	public void onDestroy() {
		if (m_song_playback != null){
			m_song_playback.stop();
		}
		stopForeground(true);
		return;
	}
	
	public Song getCurrentSong() throws Exception{
		return m_song_playback.getSong();
	}
	
	public Station getCurrentStation() {
		return m_current_station;
	}
	
	public ArrayList<Station> getStations(){
		return m_stations;
	}
	
	public boolean isPartnerAuthorized(){
		return m_pandora_remote.isPartnerAuthorized();
	}
	
	public boolean isUserAuthorized(){
		return m_pandora_remote.isUserAuthorized();
	}
	
	public void runPartnerLogin(boolean pandora_one_subscriber_flag) throws RPCException, 
																			IOException,
																			HttpResponseException,
																			Exception{
		Log.i("Pandoroid", 
			  "Running a partner login for a " +
			  (pandora_one_subscriber_flag ? "Pandora One": "standard Pandora") +
		          " subscriber.");
		m_pandora_remote.runPartnerLogin(pandora_one_subscriber_flag);
	}
	
	public void runUserLogin(String user, String password) throws HttpResponseException, 
																  RPCException,
																  IOException, 
																  Exception{
		boolean needs_partner_login = false;
		boolean is_pandora_one_user = m_pandora_remote.isPandoraOneCredentials();
		boolean failure_but_not_epic_failure = true;
		while (failure_but_not_epic_failure){
			try{
				if (needs_partner_login){
					m_prefs.edit().putBoolean("pandora_one_flag", is_pandora_one_user).apply();
					runPartnerLogin(is_pandora_one_user);
					needs_partner_login = false;
				}
				
				if (is_pandora_one_user){
					m_audio_quality = PandoraRadio.MP3_192;
				}
				else {
					 m_audio_quality = PandoraRadio.MP3_128;
				}
				Log.i("Pandoroid", "Running a user login.");
				m_pandora_remote.connect(user, password);
				failure_but_not_epic_failure = false; //Or any type of fail for that matter.
			}
			catch (SubscriberTypeException e){
				needs_partner_login = true;
				is_pandora_one_user = e.is_pandora_one;
				Log.i("Pandoroid", 
					  "Wrong subscriber type. User is a " +
					  (is_pandora_one_user? "Pandora One": "standard Pandora") +
			          " subscriber.");
			}
			catch (RPCException e){
				if (e.code == RPCException.INVALID_AUTH_TOKEN){
					needs_partner_login = true;
					Log.e("Pandoroid", e.getMessage());
				}
				else{
					throw e;
				}
			}
		}
	}
	
	public void setListener(Class<?> klass, Object listener) {
		listeners.put(klass, listener);
	}	
	
	public void setNotification() {
		if (!m_paused){
			try {
				Song tmp_song;
				tmp_song = m_song_playback.getSong();
				Notification notification = new Notification(R.drawable.icon, 
                        									 "Pandoroid Radio", 
                        									 System.currentTimeMillis());
				Intent notificationIntent = new Intent(this, PandoroidPlayer.class);
				PendingIntent contentIntent = PendingIntent.getActivity(this, 
                                   										NOTIFICATION_SONG_PLAYING, 
                               											notificationIntent, 
                               											0);
				notification.flags |= Notification.FLAG_ONGOING_EVENT | 
									  Notification.FLAG_FOREGROUND_SERVICE;
				notification.setLatestEventInfo(getApplicationContext(), 
												tmp_song.getTitle(),
												tmp_song.getArtist() + " on " + tmp_song.getAlbum(), 
												contentIntent);
				startForeground(NOTIFICATION_SONG_PLAYING, notification);
			} catch (Exception e) {}
		}
	}


//	/** methods for clients */
//	public boolean signIn(String username, String password) {
//		boolean toRet = false;
//		boolean needs_partner_login = false;
//		boolean is_pandora_one_user = false;
//		int attempts = 3;
//		
//		//Low connectivity could cause reattempts to be need to be made
//		while (attempts > 0){
//			try{
//				if (needs_partner_login){
//					synchronized(pandora_lock){
//						pandora.runPartnerLogin(is_pandora_one_user);
//					}
//					needs_partner_login = false;
//				}
//				if (is_pandora_one_user){
//					audio_quality = PandoraRadio.MP3_192;
//				}
//				else {
//					audio_quality = PandoraRadio.MP3_128;
//				}
//				synchronized(pandora_lock){
//					pandora.connect(username, password);
//					toRet = true;
//				}
//				attempts = 0;
//			}
//			catch (SubscriberTypeException e){
//				needs_partner_login = true;
//				is_pandora_one_user = e.is_pandora_one;
//				Log.i("Pandoroid", 
//						  "Wrong subscriber type. User is " +
//						  (is_pandora_one_user? "a Pandora One": "a standard Pandora") +
//				          " subscriber.");
//			}
//			catch (RPCException e){
//				if (e.code == 13){
//					--attempts;
//				}
//				else {
//					Log.e("Pandroroid","Exception logging in", e);
//					toRet = false;
//					attempts = 0;
//				}
//			}
//			catch (Exception e){
//				Log.e("Pandroroid","Exception logging in", e);
//				toRet = false;
//				attempts = 0;
//			}
//		}		
//		return toRet;
//	}
	
	public void signOut() {
		if(m_song_playback != null) {
			stopForeground(true);
			m_song_playback.stop();
		}

		if(m_pandora_remote != null) {
			m_pandora_remote.disconnect();
		}
		
		if (m_current_station != null){
			m_current_station = null;
		}
	}
	
	public boolean isAlive() {
		return m_pandora_remote.isAlive();
	}
	
	public void updateStations() throws HttpResponseException, 
										RPCException, 
										IOException, 
										Exception {
		m_stations = m_pandora_remote.getStations();
	}
	
	public boolean setCurrentStation(String station_id) {
		for(int i = 0; i < m_stations.size(); ++i){
			Station tmp_station = m_stations.get(i);
			if (tmp_station.compareTo(station_id) == 0){
				m_current_station = tmp_station;
				stopForeground(true);
				setPlaybackController();
				m_prefs.edit().putString("lastStationId", station_id).apply();
				return true;
			}
		}
		
		return false;
	}
	

	
	public void playPause(){
		if (m_song_playback != null){
			if (!m_paused){
				pause();
			}
			else{
				play();
			}
		}
	}

	private void play() {
		m_paused = false;
		m_song_playback.play();
		setNotification();
	}
	
	private void pause() {
		m_song_playback.pause();			
		m_paused = true;
		stopForeground(true);
	}
	
	

	
	public void rate(String rating) {
		if(rating == PandoroidPlayer.RATING_NONE) {
			// cannot set rating to none
			return;
		}
		
		boolean ratingBool = rating.equals(PandoroidPlayer.RATING_LOVE) ? true : false;
		try{
			m_pandora_remote.rate(m_song_playback.getSong(), ratingBool);
		}
		catch(Exception e){
			Log.e("Pandoroid", "Exception sending a song rating", e);
		}
	}
	
	public void resetPlaybackListeners(){
		if (m_song_playback != null){
			try {
				m_song_playback.setOnNewSongListener(
						(OnNewSongListener) listeners.get(OnNewSongListener.class)
						                          );
				m_song_playback.setOnPlaybackContinuedListener(
						(OnPlaybackContinuedListener) listeners.get(OnPlaybackContinuedListener.class)
															   );
				m_song_playback.setOnPlaybackHaltedListener(
						(OnPlaybackHaltedListener) listeners.get(OnPlaybackHaltedListener.class)
														   );

			} 
			catch (Exception e) {
				Log.e("Pandoroid", e.getMessage(), e);
			}
		}
	}
	
	private void setPlaybackController(){
		try{	
			if (m_song_playback == null){		
				m_song_playback = new MediaPlaybackController(m_current_station.getStationIdToken(),
						                                    PandoraRadio.AAC_32,
						                                    m_audio_quality,
						                                    m_pandora_remote,
						                                    connectivity_manager);

				
			}
			else{
				m_song_playback.reset(m_current_station.getStationIdToken(), m_pandora_remote);
				
			}
			resetPlaybackListeners();
		} 
		catch (Exception e) {
			Log.e("Pandoroid", e.getMessage(), e);
			m_song_playback = null;
		}
	}
	
	public void skip(){
		m_song_playback.skip();
	}
	
	public void startPlayback(){		
		if (m_song_playback != null){
			Thread t = new Thread(m_song_playback);
			t.start();
		}		
	}
	
	public void stopPlayback(){
		if (m_song_playback != null){
			m_song_playback.stop();
		}
		stopForeground(true);
	}
	
	/**
	 * Description: An abstract asynchronous task for doing a generic login. 
	 * @param <Params> -Parameters specific for the doInBackground() execution.
	 */
	public abstract static class ServerAsyncTask<Params> extends AsyncTask<Params, Void, Integer> {
		protected static final int ERROR_UNSUPPORTED_API = 0;
		protected static final int ERROR_NETWORK = 1;
		protected static final int ERROR_UNKNOWN = 2;
		protected static final int ERROR_REMOTE_SERVER = 3;


		/**
		 * Description: The required AsyncTask.doInBackground() function.
		 */
		protected abstract Integer doInBackground(Params... args);
		
		protected abstract void quit();
		
		protected abstract void reportAction();
		
		/**
		 * Description: A function that specifies the action to be taken
		 * 	when a user clicks the retry button in an alert dialog.
		 */
		protected abstract void retryAction();
		
		protected abstract void showAlert(AlertDialog new_alert);

		/**
		 * Description: Builds an alert dialog necessary for all login tasks.
		 * @param error -The error code.
		 * @return An alert dialog builder that can be converted into an alert
		 * 	dialog.
		 */
		protected AlertDialog.Builder buildErrorDialog(int error, final Context context) {
			AlertDialog.Builder alert_builder = new AlertDialog.Builder(context);
			alert_builder.setCancelable(false);
			alert_builder.setPositiveButton("Quit",
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							quit();
						}
					});

			switch (error) {
			case ERROR_NETWORK:
			case ERROR_UNKNOWN:
			case ERROR_REMOTE_SERVER:
				alert_builder.setNeutralButton("Retry",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								retryAction();
							}
						});
			}

			switch (error) {
			case ERROR_UNSUPPORTED_API:
				alert_builder.setNeutralButton("Report",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog,
									int which) {
								reportAction();
							}
						});
				alert_builder.setMessage("Please update the app. "
						+ "The current Pandora API is unsupported.");
				break;
			case ERROR_NETWORK:
				alert_builder.setMessage("A network error has occurred.");
				break;
			case ERROR_UNKNOWN:
				alert_builder.setMessage("An unknown error has occurred.");
				break;
			case ERROR_REMOTE_SERVER:
				alert_builder
						.setMessage("Pandora's servers are having troubles. "
								+ "Try again later.");
				break;
			}

			return alert_builder;
		}

		/**
		 * Description: A test to show off different exceptions.
		 * @throws RPCException
		 * @throws HttpResponseException
		 * @throws IOException
		 * @throws Exception
		 */
		public void exceptionTest() throws RPCException, HttpResponseException,
				IOException, Exception {
			switch (1) {
				case 0:
					throw new RPCException(
							RPCException.API_VERSION_NOT_SUPPORTED,
							"Invalid API test");
				case 1:
					throw new HttpResponseException(
							HttpStatus.SC_INTERNAL_SERVER_ERROR,
							"Internal server error test");
				case 2:
					throw new IOException("IO exception test");
				case 3:
					throw new Exception("Generic exception test");
			}
		}

		/**
		 * Description: A handler that must be called when an RPCException 
		 * 	has occurred.
		 * @param e
		 * @return
		 */
		protected int rpcExceptionHandler(RPCException e) {
			int success_flag = ERROR_UNKNOWN;
			if (RPCException.URL_PARAM_MISSING_METHOD <= e.code
					&& e.code <= RPCException.API_VERSION_NOT_SUPPORTED) {
				success_flag = ERROR_UNSUPPORTED_API;
			} else if (e.code == RPCException.INTERNAL
					|| e.code == RPCException.MAINTENANCE_MODE) {
				success_flag = ERROR_REMOTE_SERVER;
			} else {
				success_flag = ERROR_UNKNOWN;
			}

			return success_flag;
		}

		/**
		 * Description: A handler that must be called when an HttpResponseException
		 * 	has occurred.
		 * @param e
		 * @return
		 */
		protected int httpResponseExceptionHandler(HttpResponseException e) {
			int success_flag = ERROR_UNKNOWN;
			if (e.getStatusCode() == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
				success_flag = ERROR_REMOTE_SERVER;
			} else {
				success_flag = ERROR_NETWORK;
			}

			return success_flag;
		}

		/**
		 * Description: A handler that must be called when an IOException
		 * 	has been encountered.
		 * @param e
		 * @return
		 */
		protected int ioExceptionHandler(IOException e) {
			return ERROR_NETWORK;
		}

		/**
		 * Description: A handler that must be called when a generic Exception has
		 * 	been encountered.
		 * @param e
		 * @return
		 */
		protected int generalExceptionHandler(Exception e) {
			return ERROR_UNKNOWN;
		}
	}
	
	
	
//	private class PandoraDeviceLoginTask extends AsyncTask<Boolean, Void, Boolean>{
//		protected Boolean doInBackground(Boolean... subscriber_type){
//			Boolean success_flag = false;
//			try {
//				synchronized(pandora_lock){
//					m_pandora_remote.runPartnerLogin(subscriber_type[0].booleanValue());
//				}
//				success_flag = true;
//			}
//			catch (RPCException e){
//				Log.e("Pandoroid", "RPC error", e);
//			}
//			catch (Exception e){
//				Log.e("Pandoroid", "Fatal error initializing PandoraRadio", e);
//			}
//			
//			return success_flag;
//		}
//		
//		protected void onPostExecute(Boolean... success){
//			//Maybe we could do something with this information eventually....
//		}
//	}
}
