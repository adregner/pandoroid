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

import com.aregner.android.pandoid.PandoraRadioService;
import com.aregner.pandora.Song;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class PandoidPlayer extends Activity {

	public static final int REQUIRE_SELECT_STATION = 0x10;
	public static final int REQUIRE_LOGIN_CREDS = 0x20;
	public static final int GET_STATIONS_FAILED = 3;
	public static final int PLAY_RECENT = 4;
	public static final int SEARCH_ACTIVITY = 5;
	public static final String RATING_BAN = "ban";
	public static final String RATING_LOVE = "love";
	public static final String RATING_NONE = null;	
	
	private static ImageDownloader imageDownloader = new ImageDownloader();	
	private static PandoraRadioService pandora;
	private static ProgressBar progress;
	private static ProgressDialog waiting;
	private static Button playButton;
	
	private boolean initialLogin = false;
	private String lastPlayedSong = "";
	private IntentFilter intentFilter;
	private SharedPreferences prefs;
	private ImageView image, cache;
	
	private static String LOG_TAG = "PandoidPlayer";
	private static String SETUP_TAG = "InitialSetupTask";
	private static String STATION_TAG = "PlayStationTask";

	private BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(LOG_TAG, "Song Change Broadcast Received");
			
			cache = null;
			updateForNewSong();
		}
	};
	
	
	public static void imageDownloadFinished() {
		if(progress != null){
			progress.setVisibility(View.INVISIBLE);
		}
	}
	
	public static void dismissWaiting() {
		if(waiting != null && waiting.isShowing()) {
			Log.i(LOG_TAG, "Called dismissWaiting() with positive result.");
			waiting.dismiss();
		}
	}
	
	public static void togglePlayButton(){
		playButton.post(new Runnable() {
			@Override
			public void run() {
				if(playButton != null){
					if(pandora.isPlaying()){
						playButton.setText("||");
					}
					else{
						playButton.setText(">");
					}
				}
			}
		});
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(LOG_TAG, "Activity Created");
		super.onCreate(savedInstanceState);		
		setContentView(R.layout.player);
		
		this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
		
		//initializations
		//if there is a cached album cover use it.
		cache = (ImageView) getLastNonConfigurationInstance();
		playButton = (Button) findViewById(R.id.player_pause);
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		progress = (ProgressBar)findViewById(R.id.progress);
		pandora = PandoraRadioService.getInstance(false);
		
		if(pandora == null) {
			Log.i(LOG_TAG, "Service is null. Getting credentials from prefs");
			// look for what we need to continue with pandora auth
			String username = prefs.getString("pandora_username", null);
			String password = prefs.getString("pandora_password", null);

			if(username == null || password == null) {
				// bring them to the login screen so they can enter what we need
				Log.i(LOG_TAG, "Calling PandoidLogin.class");
				initialLogin = true;
				startActivityForResult(new Intent(getApplicationContext(), PandoidLogin.class).addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP), REQUIRE_LOGIN_CREDS);
			}
		}
	}
	/**
	 * Returns the current image being use for the album cover.
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		if(image != null && imageDownloader.isDoneDownloading()){
			return image;
		}
		return null;
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.player_menu, menu);
		return true;
	}

	@Override
	protected void onResume() {
		super.onResume();
		Log.i(LOG_TAG, "Resuming Activity...");
		
		if(!initialLogin){
			serviceSetup();
		}
		updateForNewSong();		
		
		//register the receiver for song changes
		Log.i(LOG_TAG, "Registering Receiver...");
		intentFilter = new IntentFilter();
		intentFilter.addAction(PandoraRadioService.SONG_CHANGE);	
		intentFilter.addAction(Intent.ACTION_SCREEN_ON); //check for need of UI update if screen has been off
		registerReceiver(receiver, intentFilter);
	}
	
	private void serviceSetup() {
		if(pandora == null || !(pandora instanceof PandoraRadioService)) {
			Log.i(LOG_TAG, "Executing InitialSetupTask");
			new InitialSetupTask().execute();
		}
	}
	
	protected void updateForNewSong() {		
		Log.i(LOG_TAG, "updateForNewSong() called..");
		if(pandora != null && pandora.isReadytoUpdateUI()){ 
			
			TextView top, bottom, middle;
			Song song;
			
			song = pandora.getCurrentSong();
			
			top = (TextView) findViewById(R.id.player_topText);
			bottom = (TextView) findViewById(R.id.player_bottomText);
			middle = (TextView)findViewById(R.id.player_middleText);
			image = (ImageView) findViewById(R.id.player_image);
			
			togglePlayButton();
			
			if(cache == null) {
				new GetAlbumArtUrlTask().execute(song);
			}	
			else 
				image.setImageDrawable(cache.getDrawable());
			
			top.setText(String.format("%s", song.getTitle()));
			middle.setText(String.format("%s", song.getArtist()));
			bottom.setText(String.format("%s", song.getAlbum()));
			
		}
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		
		if(requestCode == REQUIRE_SELECT_STATION && resultCode == RESULT_OK) {
			Log.i(LOG_TAG, "PandoraStationSelect returned with result_ok");
			pandora.setCurrentStationId(data.getLongExtra("stationId", -1));
			new PlayStationTask().execute();
		}
		else if(requestCode == REQUIRE_SELECT_STATION && resultCode == GET_STATIONS_FAILED ) {
			Log.i(LOG_TAG, "Reauthentication necessary...attempting");
			reauthenticate();
		}
		else if(requestCode == REQUIRE_LOGIN_CREDS && resultCode == RESULT_OK) {
			Log.i(LOG_TAG, "PandoraLogin.class returned ok...");
			serviceSetup();
		}
	}
	
	protected void reauthenticate() {
		pandora.signOut();
		pandora = null;
		initialLogin = true;
		serviceSetup();
	}

	public void controlButtonPressed(View button) {
		String toastMessage;
		
		switch(button.getId()) {

			case R.id.station_list:
				startActivityForResult(new Intent(getApplicationContext(), PandoidStationSelect.class), REQUIRE_SELECT_STATION);
				break;
				
			case R.id.player_ban:
				if(pandora.isPlaying()) {
					
					pandora.rate(RATING_BAN);
					toastMessage = getString(R.string.baned_song);
					
					if(prefs.getBoolean("behave_nextOnBan", true)) {
						new PlayNextTask().execute();
					}
				}
				
				else {
					toastMessage = getString(R.string.no_song);
				}
				
				Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_SHORT).show();
				break;
	
			case R.id.player_love:
				if(pandora.isPlaying()){
					pandora.rate(RATING_LOVE);
					toastMessage = getString(R.string.loved_song);
				}
				else {
					toastMessage = getString(R.string.no_song);
				}
				Toast.makeText(getApplicationContext(), toastMessage, Toast.LENGTH_SHORT).show();
				break;
	
			case R.id.player_pause:
				if(pandora.isPlayable()){
					pandora.pause();
				}
				else {
					startActivityForResult(new Intent(PandoidPlayer.this, PandoidStationSelect.class), REQUIRE_SELECT_STATION);
				}
				break;

	
			case R.id.player_next:
				if(pandora.isPlayable()) {
					new PlayNextTask().execute();			
				}
				else {
					startActivityForResult(new Intent(this, PandoidStationSelect.class), REQUIRE_SELECT_STATION);
				}
				break;
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.menu_stations:
			startActivityForResult(new Intent(getApplicationContext(), PandoidStationSelect.class), REQUIRE_SELECT_STATION);
			return true;

		case R.id.menu_logout:
			pandora.signOut();
			PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
				.putString("pandora_username", null)
				.putString("pandora_password", null)
				.commit();
			finish();
			return true;

		case R.id.menu_settings:
			startActivity(new Intent(getApplicationContext(), PandoidSettings.class));
			return true;
		
		case R.id.menu_recently_played:
			startActivity(new Intent(getApplicationContext(), PandoidRecentlyPlayedList.class));

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/** Signs in the user and loads their initial data
	 *     -> brings them toward a station               */
	class InitialSetupTask extends AsyncTask<Void, Void, Boolean>{
		@Override
		protected void onPreExecute() {
			Log.i(SETUP_TAG,"Starting task...");
			lockOrientation();
			waiting = ProgressDialog.show(PandoidPlayer.this, "", getString(R.string.signing_in));
		}

		@Override
		protected Boolean doInBackground(Void... arg) {
			PandoraRadioService.createPandoraRadioService(PandoidPlayer.this);
			pandora = PandoraRadioService.getInstance(true);
			
			String username = prefs.getString("pandora_username", null);
			String password = prefs.getString("pandora_password", null);
			
			try {
				Log.i(SETUP_TAG, "Attempting to sign in using prefs credentials...");
				pandora.signIn(username, password);
			} catch(Exception ex) {
				Log.e(SETUP_TAG, "Failed to sign in...", ex);
				ex.printStackTrace();
			}
			return pandora.isAlive();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			
			Log.i(SETUP_TAG, "Finished signin...checking results");
			Log.i(SETUP_TAG, "calling dismissWaiting()");
			unlockOrientation();
			dismissWaiting();

			if(result.booleanValue() && result) {
				Log.i(SETUP_TAG, "Sign in success...");
				if(!pandora.isPlaying()) {

					if(pandora.isPlayable()) {
						// play it or resume playback or something smart like that
						Log.i(SETUP_TAG, "Calling new PlayStationTask...");
						(new PlayStationTask()).execute();
					}
					else {
						// ask them to select a station
						Log.i(SETUP_TAG, "Need station. Calling PandoidStationSelect.class...");
						startActivityForResult(new Intent(PandoidPlayer.this, PandoidStationSelect.class), REQUIRE_SELECT_STATION);
					}
				}
			}
			else {
				// failed to sign in for some reason
				Log.e(SETUP_TAG, "Sign in failed...Calling PandoidLogin.class");
				Toast.makeText(PandoidPlayer.this, getString(R.string.signin_failed), Toast.LENGTH_SHORT).show();
				startActivityForResult(new Intent(getApplicationContext(), PandoidLogin.class).addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP), REQUIRE_LOGIN_CREDS);
			}
		}
	}
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if ((keyCode == KeyEvent.KEYCODE_SEARCH)) {
	        Log.d(this.getClass().getName(), "Search Key pressed");
	        startActivityForResult(new Intent(getApplicationContext(), PandoidSearchActivity.class), REQUIRE_SELECT_STATION);
	    }
	    return super.onKeyDown(keyCode, event);
	}

	/** Prepares a selected station to be played */
	private class PlayStationTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			Log.i(STATION_TAG, "Starting...ataching activity");
			lockOrientation();
			waiting = ProgressDialog.show(PandoidPlayer.this, "",  getString(R.string.loading, pandora.getCurrentStation().getName()));
		}

		@Override
		protected Void doInBackground(Void... arg0) {
			pandora.prepare();
			
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			Log.i(STATION_TAG, "In postExecute");
			unlockOrientation();
			pandora.play();
			dismissWaiting();
		}
	} 
	class PlayNextTask extends AsyncTask<Void, Void, Void>{
		
		@Override
		protected void onPreExecute() {
			lockOrientation();
			waiting = ProgressDialog.show(PandoidPlayer.this, "",  getString(R.string.next_song));
		}
		@Override
		protected Void doInBackground(Void ...voids) {
			pandora.next();
			return null;
		}
		@Override
		protected void onPostExecute(Void result) {
			unlockOrientation();
			dismissWaiting();
		}	
	}
	class GetAlbumArtUrlTask extends AsyncTask<Song, Void, String> {
		
		@Override
		protected void onPreExecute(){
			image.setImageBitmap(null);
			progress = (ProgressBar) findViewById(R.id.progress);
			if(progress != null){
				progress.setVisibility(View.VISIBLE);
			}
		}
		@Override
		protected String doInBackground(Song... params) {
			
			String url;
			Song song = params[0];
			
			String albumResPref = prefs.getString("pandora_albumArtRes", "0");
			int preference = Integer.parseInt(albumResPref);
			
			AlbumArtDownloader aad = new AlbumArtDownloader(song);
			url = aad.getAlbumUrl(preference);
			
			if(url == null ||  url.length() == 0) {
				Log.i(LOG_TAG, "Couldn't find lastFm album artwork, reverting to pandora...");
				 url = song.getAlbumCoverUrl();
			}
			Log.i(LOG_TAG,"album url = " + url);
					
			return url;
		}
		@Override
		protected void onPostExecute(String url){
			if(url != null){
				
				ImageView image = (ImageView)findViewById(R.id.player_image);
				imageDownloader.download(url, image);
			}
		}
	}
	
	public void lockOrientation() {
		this.setRequestedOrientation(getResources().getConfiguration().orientation);
		Log.i(LOG_TAG, "Locking orientation: " + getResources().getConfiguration().orientation );
	}
	public void unlockOrientation() {
		this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		Log.i(LOG_TAG, "Unlocking orientation: " + getResources().getConfiguration().orientation);
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		unregisterReceiver(receiver);
		dismissWaiting();
	}
}