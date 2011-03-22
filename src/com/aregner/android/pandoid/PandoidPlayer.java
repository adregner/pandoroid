package com.aregner.android.pandoid;

import com.aregner.pandora.PandoraRadio.Song;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class PandoidPlayer extends Activity {

	public static final int REQUIRE_SELECT_STATION = 0x10;
	public static final int REQUIRE_LOGIN_CREDS = 0x20;
	public static final String RATING_BAN = "ban";
	public static final String RATING_LOVE = "love";
	public static final String RATING_NONE = null;

	private static ProgressDialog waiting;
	private PandoraRadioService pandora;
	private SharedPreferences prefs;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
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
				startActivityForResult(new Intent(getApplicationContext(), PandoidLogin.class), REQUIRE_LOGIN_CREDS);
			}
			else {
				// all set, start fancy stuff
				(new InitialSetupTask()).execute(username, password);
				// TODO : we could just call the StationSelect activity here and have it 
				// or a common routine handle the initial login so that there is no
				// pause on the empty player screen
			}
		}
		else {
			pandora = PandoraRadioService.getInstance(false);
			updateForNewSong(pandora.getCurrentSong());
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.player_menu, menu);
		return true;
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// The activity has become visible (it is now "resumed").
	}

	protected void updateForNewSong(Song song) {
		TextView top = (TextView) findViewById(R.id.player_topText);
		TextView bottom = (TextView) findViewById(R.id.player_bottomText);
		ImageView image = (ImageView) findViewById(R.id.player_image);

		top.setText(String.format("%s by %s", song.getTitle(), song.getArtist()));
		bottom.setText(String.format("%s", song.getAlbum()));
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUIRE_SELECT_STATION && resultCode == RESULT_OK) {
			pandora.setCurrentStationId(data.getLongExtra("stationId", -1));
			(new PlayStationTask()).execute();
		}
		else if(requestCode == REQUIRE_LOGIN_CREDS && resultCode == RESULT_OK) {
			String username = prefs.getString("pandora_username", null);
			String password = prefs.getString("pandora_password", null);
			(new InitialSetupTask()).execute(username, password);
		}
	}

	public void controlButtonPressed(View button) {
		switch(button.getId()) {

		case R.id.player_ban:
			pandora.rate(RATING_BAN);
			Toast.makeText(getApplicationContext(), "Buzz-kill...", Toast.LENGTH_SHORT).show();
			if(prefs.getBoolean("behave_nextOnBan", true)) {
				updateForNewSong(pandora.next());
			}
			break;

		case R.id.player_love:
			pandora.rate(RATING_LOVE);
			Toast.makeText(getApplicationContext(), "Rock on!", Toast.LENGTH_SHORT).show();
			break;

		case R.id.player_pause:
			pandora.pause();
			break;

		case R.id.player_next:
			updateForNewSong(pandora.next());
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
			startActivityForResult(new Intent(getApplicationContext(), PandoidLogin.class), REQUIRE_LOGIN_CREDS);
			return true;

		case R.id.menu_settings:
			startActivity(new Intent(getApplicationContext(), PandoidSettings.class));
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/** Signs in the user and loads their initial data
	 *     -> brings them toward a station               */
	private class InitialSetupTask extends AsyncTask<String, Void, Boolean> {
		@Override
		protected void onPreExecute() {
			waiting = ProgressDialog.show(PandoidPlayer.this, "",  "Signing in. Please wait...");
		}

		@Override
		protected Boolean doInBackground(String... auth) {
			PandoraRadioService.createPandoraRadioService(getApplicationContext());
			pandora = PandoraRadioService.getInstance(true);
			try {
				pandora.signIn(auth[0], auth[1]);
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
						startActivityForResult(new Intent(getApplicationContext(), PandoidStationSelect.class), REQUIRE_SELECT_STATION);
					}
				}
			}
			else {
				// failed to sign in for some reason
				Toast.makeText(getApplicationContext(), "Sign-in failed", Toast.LENGTH_SHORT).show();
				startActivityForResult(new Intent(getApplicationContext(), PandoidLogin.class), REQUIRE_LOGIN_CREDS);
			}
		}
	}

	/** Prepares a selected station to be played */
	private class PlayStationTask extends AsyncTask<Void, Void, Void> {
		@Override
		protected void onPreExecute() {
			waiting = ProgressDialog.show(PandoidPlayer.this, "",  String.format("Loading %s...", pandora.getCurrentStation().getName()));
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
		// Another activity is taking focus (this activity is about to be "paused").
	}

	@Override
	protected void onStop() {
		super.onStop();
	}
}