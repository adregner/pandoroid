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
import com.pandoroid.android.R;

import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class PandoroidLogin extends SherlockActivity {
	private SharedPreferences prefs;
	private PandoraRadioService pandora;
	private boolean m_is_bound;
	private static ProgressDialog waiting;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		//PandoraRadioService.createPandoraRadioService(getBaseContext());
		doBindService();		


		this.getSupportActionBar().setTitle(R.string.signin_welcome);
		((Button)findViewById(R.id.login_button)).setOnClickListener(new OnClickListener() {
			public void onClick(View viewParam) {
				String sUserName = ((EditText)findViewById(R.id.login_username)).getText().toString();
				String sPassword = ((EditText)findViewById(R.id.login_password)).getText().toString();

				// this just catches the error if the program can't locate the GUI stuff
				if(sUserName != null && sPassword != null && sUserName.length() > 1 && sPassword.length() > 1) {
					boolean success = prefs.edit()
						.putString("pandora_username", sUserName)
						.putString("pandora_password", sPassword)
						.commit();

					if(success) {
						new LoginTask().execute();
					}
				}
			}
		});
	}

	@Override
	protected void onResume() {
		super.onResume();
	}
	
	protected void onDestroy(){
		super.onDestroy();
		doUnbindService();
	}
	
	private class LoginTask extends AsyncTask<String, Void, Boolean>{

		protected void onPreExecute(){
			waiting = ProgressDialog.show(PandoroidLogin.this, "",  getString(R.string.signing_in));
		}
		
		@Override
		protected Boolean doInBackground(String... params) {
			String username = prefs.getString("pandora_username", null);
			String password = prefs.getString("pandora_password", null);
			if(username == null || password == null){
				return false;
			}
			return pandora != null && pandora.signIn(username, password);
		}
		
		@Override
		protected void onPostExecute(Boolean result){
			waiting.dismiss();
			if(result.booleanValue()){
				//Start the PandoroidPlayer activity
				//startActivity(new Intent(PandoroidLogin.this, PandoroidPlayer.class));
				finish();
				
			} else {
				Toast.makeText(PandoroidLogin.this, R.string.signin_failed, 2000).show();
			}
		}
		
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
		    
			String username = prefs.getString("pandora_username", null);
			String password = prefs.getString("pandora_password", null);
			
			//Set the username and/or login
			EditText user = (EditText)findViewById(R.id.login_username);
			if(username != null){
				user.setText(username);
				if(password != null){
					new LoginTask().execute();
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
	        m_is_bound = false;
	    }
	}
}
