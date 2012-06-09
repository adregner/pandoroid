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
package com.aregner.android.pandoroid;

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import com.aregner.android.pandoroid.R;
import com.aregner.pandora.Song;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


public class PandoroidPlayer extends SherlockActivity {

	public static final int REQUIRE_SELECT_STATION = 0x10;
	public static final int REQUIRE_LOGIN_CREDS = 0x20;
	public static final String RATING_BAN = "ban";
	public static final String RATING_LOVE = "love";
	public static final String RATING_NONE = null;

	private static ProgressDialog waiting;
	private PandoraRadioService pandora;
	private SharedPreferences prefs;
	private ImageDownloader imageDownloader = new ImageDownloader();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		//Disable StrictMode for 3.0+
		StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
		setTheme(R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);

		if(PandoraRadioService.getInstance(false) == null) {
			// handle for the preferences for us to use everywhere
			prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

			// look for what we need to continue with pandora auth
			String username = prefs.getString("pandora_username", null);
			String password = prefs.getString("pandora_password", null);

			if(username == null || password == null) {
				// bring them to the login screen so they can enter what we need
				startActivityForResult(new Intent(getApplicationContext(), PandoroidLogin.class), REQUIRE_LOGIN_CREDS);
			}
		}
		else {
			pandora = PandoraRadioService.getInstance(false);
			updateForNewSong(pandora.getCurrentSong());
		}
	}

	
	public boolean onCreateOptionsMenu(Menu menu) {
		SubMenu sub = menu.addSubMenu("Options");
		sub.add(0, R.id.menu_stations, Menu.NONE, R.string.menu_stations);
		sub.add(0, R.id.menu_settings, Menu.NONE, R.string.menu_settings);
		sub.add(0, R.id.menu_logout, Menu.NONE, R.string.menu_logout);
		
		MenuItem subMenu = sub.getItem();
		subMenu.setIcon(R.drawable.ic_sysbar_menu);
		subMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// The activity has become visible (it is now "resumed").
		serviceSetup();
	}
	
	private void serviceSetup() {
		if(pandora == null || !(pandora instanceof PandoraRadioService)) {
			(new InitialSetupTask()).execute();
		}
	}

	protected void updateForNewSong(Song song) {
		this.getSupportActionBar().setTitle(String.format(""+song.getTitle()));
		TextView top = (TextView) findViewById(R.id.player_topText);
		//TextView bottom = (TextView) findViewById(R.id.player_bottomText);
		ImageView image = (ImageView) findViewById(R.id.player_image);

		//top.setText(String.format("%s by %s", song.getTitle(), song.getArtist()));
		imageDownloader.download(song.getAlbumCoverUrl(), image);
		top.setText(String.format("%s\n%s", song.getArtist(), song.getAlbum()));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUIRE_SELECT_STATION && resultCode == RESULT_OK) {
			pandora.setCurrentStationId(data.getLongExtra("stationId", -1));
			(new PlayStationTask()).execute();
		}
		else if(requestCode == REQUIRE_LOGIN_CREDS) {
			//serviceSetup();
			new InitialSetupTask().execute();
		}
	}

	public void controlButtonPressed(View button) {
		switch(button.getId()) {

		case R.id.player_ban:
			pandora.rate(RATING_BAN);
			Toast.makeText(getApplicationContext(), getString(R.string.baned_song), Toast.LENGTH_SHORT).show();
			if(prefs.getBoolean("behave_nextOnBan", true)) {
				updateForNewSong(pandora.next());
			}
			break;

		case R.id.player_love:
			pandora.rate(RATING_LOVE);
			Toast.makeText(getApplicationContext(), getString(R.string.loved_song), Toast.LENGTH_SHORT).show();
			break;

		case R.id.player_pause:
			pandora.pause();
			break;

		case R.id.player_next:
			updateForNewSong(pandora.next());
			break;
		}
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {

		case R.id.menu_stations:
			startActivityForResult(new Intent(getApplicationContext(), PandoroidStationSelect.class), REQUIRE_SELECT_STATION);
			return true;

		case R.id.menu_logout:
			pandora.signOut();
			PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
				.putString("pandora_username", null)
				.putString("pandora_password", null)
				.commit();
			startActivityForResult(new Intent(getApplicationContext(), PandoroidLogin.class), REQUIRE_LOGIN_CREDS);
			return true;

		case R.id.menu_settings:
			startActivity(new Intent(getApplicationContext(), PandoroidSettings.class));
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/** Signs in the user and loads their initial data
	 *     -> brings them toward a station               */
	private class InitialSetupTask extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected void onPreExecute() {
			waiting = ProgressDialog.show(PandoroidPlayer.this, "",  getString(R.string.signing_in));
		}

		@Override
		protected Boolean doInBackground(Void... arg) {
			PandoraRadioService.createPandoraRadioService(getApplicationContext());
			pandora = PandoraRadioService.getInstance(true);
			
			String username = prefs.getString("pandora_username", null);
			String password = prefs.getString("pandora_password", null);
			
			try {
				if (username != null && password != null){
					pandora.signIn(username, password);
				}
			} catch(Exception ex) {
				ex.printStackTrace();
			}
			return pandora.isAlive();
		}

		@Override
		protected void onPostExecute(Boolean result) {

			dismissWaiting();

			if(result.booleanValue()) {

				if(!pandora.isPlaying()) {

					if(pandora.isPlayable()) {
						// play it or resume playback or something smart like that
						(new PlayStationTask()).execute();
					}
					else {
						// ask them to select a station
						startActivityForResult(new Intent(getApplicationContext(), PandoroidStationSelect.class), REQUIRE_SELECT_STATION);
					}
				}
			}
			else {
				// failed to sign in for some reason
				Toast.makeText(getApplicationContext(), getString(R.string.signin_failed), Toast.LENGTH_SHORT).show();
				startActivityForResult(new Intent(getApplicationContext(), PandoroidLogin.class), REQUIRE_LOGIN_CREDS);
			}
		}
	}

	/** Prepares a selected station to be played */
	private class PlayStationTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			waiting = ProgressDialog.show(PandoroidPlayer.this, "",  getString(R.string.loading, pandora.getCurrentStation().getName()));
		}

		@Override
		protected Void doInBackground(Void... arg0) {
			pandora.setListener(OnCompletionListener.class, new OnCompletionListener() {
				public void onCompletion(MediaPlayer mp) {
					updateForNewSong(pandora.next());
				}
			});
			pandora.setListener(OnPreparedListener.class, new OnPreparedListener() {
				public void onPrepared(MediaPlayer mp) {
				}
			});
			pandora.prepare();

			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			updateForNewSong(pandora.play());
			dismissWaiting();
		}
	}

	public static void dismissWaiting() {
		if(waiting != null && waiting.isShowing()) {
			waiting.dismiss();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		dismissWaiting();
		// Another activity is taking focus (this activity is about to be "paused").
	}

	@Override
	protected void onStop() {
		super.onStop();
		dismissWaiting();
	}
}