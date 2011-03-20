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
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class PandoidPlayer extends Activity {

	public static final int REQUIRE_SELECT_STATION = 0x10;
	public static final int REQUIRE_LOGIN_CREDS = 0x20;
	
	private static ProgressDialog waiting;
	private PandoraRadioService pandora;
	private SharedPreferences prefs;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);

		// start the grunt service we will be wanting to use soon
		PandoraRadioService.createPandoraRadioService(this);
		
		// handle for the preferences for us to use everywhere
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

		// look for what we need to continue with pandora auth
		String username = prefs.getString("pandora_username", null);
		String password = prefs.getString("pandora_password", null);

		if(username == null || password == null) {
			// bring them to the login screen so they can enter what we need
			startActivityForResult(new Intent(getApplicationContext(), PandoidLogin.class), REQUIRE_LOGIN_CREDS);
		}
		else if(pandora == null || ! pandora.isAlive()) {
			// all set, start fancy stuff
			(new InitialSetupTask()).execute(username, password);
		}
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
			Toast.makeText(getApplicationContext(), 
					"Sorry that you don't like the song, but rating has not been implemented yet.", Toast.LENGTH_LONG).show();
			break;
		
		case R.id.player_love:
			Toast.makeText(getApplicationContext(), 
					"Great that you love the song, but rating has not been implemented yet.", Toast.LENGTH_LONG).show();
			break;
		
		case R.id.player_pause:
			pandora.pause();
			break;
		
		case R.id.player_next:
			updateForNewSong(pandora.next());
			break;
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
			pandora = PandoraRadioService.getInstance();
			try {
				pandora.signIn(auth[0], auth[1]);
			} catch(Exception ex) {
				ex.printStackTrace();
			}
			return pandora.isAlive();
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if(result.booleanValue()) {
				if(pandora.isPlayable() && !pandora.isPlaying()) {
					// play it or resume playback or something smart like that
					(new PlayStationTask()).execute();
				}
				else {
					// ask them to select a station
					startActivityForResult(new Intent(getApplicationContext(), PandoidStationSelect.class), REQUIRE_SELECT_STATION);
				}
			}
			else {
				
			}
			dismissWaiting();
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