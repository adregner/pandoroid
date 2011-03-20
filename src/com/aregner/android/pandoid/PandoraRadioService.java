package com.aregner.android.pandoid;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import com.aregner.pandora.PandoraRadio;
import com.aregner.pandora.PandoraRadio.Song;
import com.aregner.pandora.PandoraRadio.Station;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.IBinder;

public class PandoraRadioService extends Service {

	// tools this service uses
	private PandoraRadio pandora;
	private MediaPlayer media;
	
	// tracking/organizing what we are doing
	private Station currentStation;
	private Song[] currentPlaylist;
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
	public static PandoraRadioService getInstance() {
		long startedWaiting = System.currentTimeMillis();
		while( instance == null && System.currentTimeMillis() - startedWaiting < 5000L ) {
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

	/** methods for clients */
	public void signIn(String username, String password) {
		pandora.connect(username, password);
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
	public void prepare() {
		currentPlaylist = currentStation.getPlaylist();
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
		return currentPlaylist[i];
	}
	public void pause() {
		if(media.isPlaying())
			media.pause();
		else
			media.start();
	}
	public Song next() {
		if(++currentSongIndex < currentPlaylist.length) {
			prepare(currentSongIndex);
			return play(currentSongIndex);
		}
		else {
			// get a new playlist
			prepare();
			return play();
		}
	}
}
