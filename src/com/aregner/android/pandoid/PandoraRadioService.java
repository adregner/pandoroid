package com.aregner.android.pandoid;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import com.aregner.pandora.PandoraRadio;
import com.aregner.pandora.PandoraRadio.Song;
import com.aregner.pandora.PandoraRadio.Station;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PandoraRadioService extends Service {

	private static final int NOTIFICATION_SONG_PLAYING = 1;
	
	// tools this service uses
	private PandoraRadio pandora;
	private MediaPlayer media;
	
	private NotificationManager notificationManager;
	private TelephonyManager telephonyManager;
	private SharedPreferences prefs;
	
	// tracking/organizing what we are doing
	private Station currentStation;
	private Song[] currentPlaylist;
	private Song[] nextPlaylist;
	private int currentSongIndex;
	private HashMap<Class<?>,Object> listeners = new HashMap<Class<?>,Object>();

	
	// static usefullness
	private static PandoraRadioService instance;
	private static Object lock = PandoraRadioService.class;

	public static void createPandoraRadioService(Context context) {
		synchronized(lock) {
			if(instance == null) {
				context.startService(new Intent(context, PandoraRadioService.class));
			}
		}
	}
	public static PandoraRadioService getInstance(boolean wait) {
		long startedWaiting = System.currentTimeMillis();
		while( wait && instance == null && System.currentTimeMillis() - startedWaiting < 5000L ) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				e.printStackTrace();
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
			
			notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

			// Register the listener wit the telephony manager
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
	
	public void setListener(Class<?> klass, Object listener) {
		listeners.put(klass, listener);
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
	public ArrayList<Station> getStations() {
		return pandora.getStations();
	}
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
	public void prepare() {
		currentPlaylist = currentStation.getPlaylist( prefs.getString("pandora_audioFormat", PandoraRadio.DEFAULT_AUDIO_FORMAT) );
		prepare(0);
	}
	public void prepare(int i) {
		currentSongIndex = i;
		media.reset();
		
		media.setOnCompletionListener((OnCompletionListener)listeners.get(OnCompletionListener.class));
		media.setOnPreparedListener((OnPreparedListener)listeners.get(OnPreparedListener.class));
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
		media.start();
		setNotification();
		return currentPlaylist[i];
	}
	public void pause() {
		if(media.isPlaying()) {
			media.pause();
			stopForeground(true);
		}
		else {
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
}
