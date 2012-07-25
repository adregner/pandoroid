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

import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.SubMenu;
import com.pandoroid.pandora.Song;
import com.pandoroid.playback.OnNewSongListener;
import com.pandoroid.android.R;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
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
	
	//private static final String STATE_IMAGE = "imageDownloader";

	private static ProgressDialog waiting;
	private PandoraRadioService pandora;
	private SharedPreferences prefs;
	private boolean m_is_bound;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.player);

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		doBindService();
	}
	
//	public void onRestoreInstanceState(Bundle savedInstanceState){
//		super.onRestoreInstanceState(savedInstanceState);
//	}

	
	public boolean onCreateOptionsMenu(Menu menu) {
		SubMenu sub = menu.addSubMenu("Options");
		sub.add(0, R.id.menu_stations, Menu.NONE, R.string.menu_stations);
		sub.add(0, R.id.menu_settings, Menu.NONE, R.string.menu_settings);
		sub.add(0, R.id.menu_logout, Menu.NONE, R.string.menu_logout);
		sub.add(0, R.id.menu_about, Menu.NONE, R.string.menu_about);
		
		MenuItem subMenu = sub.getItem();
		subMenu.setIcon(R.drawable.ic_sysbar_menu);
		subMenu.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onStart() {
		super.onStart();
		
//		if (pandora == null || !pandora.isAlive()){
//			PandoroidPlayer.this.startActivity(new Intent(PandoroidPlayer.this, PandoroidLogin.class));
//		}
		if (m_is_bound && pandora.isAlive()){
			pandora.setListener(OnNewSongListener.class, new OnNewSongListener() {
				public void onNewSong(Song song) {
					updateForNewSong(song);
				}
			});
			if (pandora.song_playback == null){
				String lastStationId = "";
				try {
					lastStationId = prefs.getString("lastStationId", "");
				}
				catch (ClassCastException e){
					prefs.edit().remove("lastStationId").commit();
				}
				
				if(!pandora.setCurrentStationId(lastStationId)){
					//Get a station
					startActivityForResult(new Intent(getApplicationContext(), 
							               PandoroidStationSelect.class), 
							               REQUIRE_SELECT_STATION);
				} else {	

					pandora.startPlayback();
				}
			}
			else{
				try{
					songRefresh(pandora.song_playback.getSong());
				}
				catch(Exception e){}
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
	}

	protected void updateForNewSong(Song song) {
		pandora.setNotification();
		songRefresh(song);
	}
	
	protected void songRefresh(Song song){
		this.getSupportActionBar().setTitle(String.format(""+song.getTitle()));
		TextView top = (TextView) findViewById(R.id.player_topText);
		//TextView bottom = (TextView) findViewById(R.id.player_bottomText);
		ImageView image = (ImageView) findViewById(R.id.player_image);

		//top.setText(String.format("%s by %s", song.getTitle(), song.getArtist()));
		pandora.image_downloader.download(song.getAlbumCoverUrl(), image);
		top.setText(String.format("%s\n%s", song.getArtist(), song.getAlbum()));

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUIRE_SELECT_STATION && resultCode == RESULT_OK) {
			pandora.setCurrentStationId(data.getStringExtra("stationId"));
			pandora.startPlayback();
		}
	}

	public void controlButtonPressed(View button) {
		switch(button.getId()) {
		case R.id.player_ban:
			pandora.rate(RATING_BAN);
			Toast.makeText(getApplicationContext(), getString(R.string.baned_song), Toast.LENGTH_SHORT).show();
			if(prefs.getBoolean("behave_nextOnBan", true)) {
				pandora.song_playback.skip();
			}
			break;

		case R.id.player_love:
			pandora.rate(RATING_LOVE);
			Toast.makeText(getApplicationContext(), getString(R.string.loved_song), Toast.LENGTH_SHORT).show();
			break;

		case R.id.player_pause:
			pandora.playPause();
			break;

		case R.id.player_next:
			pandora.song_playback.skip();
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
		
		case R.id.menu_about:
			startActivity(new Intent(getApplicationContext(), AboutDialog.class));
			return true;

		default:
			return super.onOptionsItemSelected(item);
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

//	public void onSaveInstanceState(Bundle savedInstanceState){
//		savedInstanceState
//	}
	
	@Override
	protected void onStop() {
		super.onStop();
		dismissWaiting();
	}
	
	protected void onDestroy(){
		super.onDestroy();
		doUnbindService();
	}
	
	//Necessary service stuff taken straight from the developer reference for Service
	private ServiceConnection m_connection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className, IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  Because we have bound to a explicit
	        // service that we know is running in our own process, we can
	        // cast its IBinder to a concrete class and directly access it.
	        pandora = ((PandoraRadioService.PandoraRadioBinder)service).getService();
		    m_is_bound = true;
			if (!pandora.isAlive()){				
				PandoroidPlayer.this.startActivity(new Intent(PandoroidPlayer.this, PandoroidLogin.class));
			}
			else{// (pandora != null && pandora.isAlive()){

				pandora.setListener(OnNewSongListener.class, new OnNewSongListener() {
					public void onNewSong(Song song) {
						updateForNewSong(song);
					}
				});
				if (pandora.song_playback == null){
					String lastStationId = "";
					try {
						lastStationId = prefs.getString("lastStationId", "");
					}
					catch (ClassCastException e){
						prefs.edit().remove("lastStationId").commit();
					}
					
					if(!pandora.setCurrentStationId(lastStationId)){
						//Get a station
						startActivityForResult(new Intent(getApplicationContext(), 
								               PandoroidStationSelect.class), 
								               REQUIRE_SELECT_STATION);
					} else {	

						pandora.startPlayback();
					}
				}
				else{
					pandora.resetPlaybackListeners();
					try{
						songRefresh(pandora.song_playback.getSong());
					}
					catch(Exception e){}
				}
			}
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        // Because it is running in our same process, we should never
	        // see this happen.
	        pandora = null;
	        m_is_bound = false;
	    }  
	};

	void doBindService() {
		
		//This is the master service start
		startService(new Intent(this, PandoraRadioService.class));
		
	    // Establish a connection with the service.  We use an explicit
	    // class name because we want a specific service implementation that
	    // we know will be running in our own process (and thus won't be
	    // supporting component replacement by other applications).
	    bindService(new Intent(this, 
	                PandoraRadioService.class), 
	                m_connection, Context.BIND_AUTO_CREATE);



	}

	void doUnbindService() {
	    if (m_is_bound) {
	        // Detach our existing connection.
	        unbindService(m_connection);
	    }
	}

}