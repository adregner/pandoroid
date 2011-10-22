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

import java.util.ArrayList;
import java.util.Map;

import com.aregner.android.pandoid.PandoraRadioService;
import com.aregner.pandora.SearchResult;
import com.aregner.pandora.Song;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class PandoidPlayer extends Activity {

	public static final int REQUIRE_SELECT_STATION = 0x10;
	public static final int REQUIRE_LOGIN_CREDS = 0x20;
	public static final int GET_STATIONS_FAILED = 3;
	public static final int PLAY_RECENT = 4;
	public static final String RATING_BAN = "ban";
	public static final String RATING_LOVE = "love";
	public static final String RATING_NONE = null;
	

	private static ProgressDialog waiting;
	private PandoraRadioService pandora;
	private SharedPreferences prefs;
	private boolean initialLogin = false;
	private static ImageDownloader imageDownloader = new ImageDownloader();
	ImageView image, cache;
		
	private static  String LOG_TAG = "PandoidPlayer";
	private static String SETUP_TAG = "InitialSetupTask";
	private static String STATION_TAG = "PlayStationTask";
	
	IntentFilter intentFilter = new IntentFilter();
	private BroadcastReceiver receiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			Log.i(LOG_TAG, "Song Change Broadcast Received");
			//deleted cached image
			cache = null;
			updateForNewSong();
		}
	};

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(LOG_TAG, "Activity Created");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);
		
		//if there is a cached album cover use it.
		cache = (ImageView) getLastNonConfigurationInstance();
		// handle for the preferences for us to use everywhere
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		
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
		
		Log.i(LOG_TAG, "Registering Receiver...");
		intentFilter.addAction(PandoraRadioService.SONG_CHANGE);	
		intentFilter.addAction(Intent.ACTION_SCREEN_ON); //check for need of UI update if screen has been off
		registerReceiver(receiver, intentFilter);
		
		if(!initialLogin){
			serviceSetup();
		}
		
		updateForNewSong();
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
			
			String url;
			TextView top, bottom;
			Button player_pause;
			Song song;
			
			song = pandora.getCurrentSong();
			
			top = (TextView) findViewById(R.id.player_topText);
			bottom = (TextView) findViewById(R.id.player_bottomText);
			image = (ImageView) findViewById(R.id.player_image);
			player_pause = (Button) findViewById(R.id.player_pause);
			
			if(pandora.isPlaying())
				player_pause.setText("||");
			
			if(cache == null) {
				url = getImageUrl(song);
				
				if(url == null ||  url.length() == 0) {
					Log.i(LOG_TAG, "Couldn't find lastFm album artwork, reverting to pandora...");
					 url = song.getAlbumCoverUrl();
				}
				Log.i(LOG_TAG,"album url = " + url);
				imageDownloader.download(url, image);
			}
				
			else 
				image.setImageDrawable(cache.getDrawable());
			
			top.setText(String.format("%s by %s", song.getTitle(), song.getArtist()));
			bottom.setText(String.format("%s", song.getAlbum()));
		}
	}

	protected String getImageUrl(Song song) {

		String albumResPref = prefs.getString("pandora_albumArtRes", "0");
		int preference = Integer.parseInt(albumResPref);
		Log.i(LOG_TAG,"Using " + albumResPref + " image resolution");
		AlbumArtDownloader aad = new AlbumArtDownloader(song);
		return aad.getAlbumUrl(preference);
		
	}
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUIRE_SELECT_STATION && resultCode == RESULT_OK) {
			Log.i(LOG_TAG, "PandoraStationSelect returned with result_ok");
			pandora.setCurrentStationId(data.getLongExtra("stationId", -1));
			new PlayStationTask().execute();
		}
		else if(requestCode == REQUIRE_SELECT_STATION && resultCode == this.GET_STATIONS_FAILED ) {
			Log.i(LOG_TAG, "Reauthentication necessary...attempting");
			pandora.signOut();
			pandora = null;
			serviceSetup();
		}
		else if(requestCode == REQUIRE_LOGIN_CREDS && resultCode == RESULT_OK) {
			Log.i(LOG_TAG, "PandoraLogin.class returned ok...");
			serviceSetup();
		}
		else if(requestCode == REQUIRE_LOGIN_CREDS && resultCode != RESULT_OK ) {
			Log.i(LOG_TAG, "PandoidLogin.class returned with bad result. finishing activity");
			finish();
		}
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
					if(pandora.isPlaying()){
						((Button)button).setText(">");
					}
					else {
						((Button)button).setText("||");
					}
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
			//startActivityForResult(new Intent(getApplicationContext(), PandoidLogin.class), REQUIRE_LOGIN_CREDS);
			return true;

		case R.id.menu_settings:
			startActivity(new Intent(getApplicationContext(), PandoidSettings.class));
			return true;
		
		case R.id.menu_recently_played:
			startActivityForResult(new Intent(getApplicationContext(), PandoidRecentlyPlayedList.class), PLAY_RECENT);

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
			
	//		ArrayList<SearchResult> searchResults = pandora.search("Switchfoot");
	//		Log.i(LOG_TAG, "Got results");
		}
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
	public static void dismissWaiting() {
		if(waiting != null && waiting.isShowing()) {
			Log.i(LOG_TAG, "Called dismissWaiting() with positive result.");
			waiting.dismiss();
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

	@Override
	protected void onStop() {
		super.onStop();
		dismissWaiting();
	}
	@Override
	protected void onDestroy() {
		super.onDestroy();
		dismissWaiting();
	}
}