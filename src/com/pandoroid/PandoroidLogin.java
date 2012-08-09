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

import com.actionbarsherlock.app.SherlockActivity;
import com.pandoroid.R;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class PandoroidLogin extends SherlockActivity {
	private SharedPreferences m_prefs;

	/*
	 * Activity start and end stuff
	 */
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setTheme(R.style.Theme_Sherlock);
		setContentView(R.layout.login);
		
		m_prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());	

		getSupportActionBar().setTitle(R.string.signin_welcome);
		Button login_button = (Button) findViewById(R.id.login_button);		
		login_button.setOnClickListener(new SignInButtonClickListener());
		
		//Set the displayed username if there is one
		String username = m_prefs.getString("pandora_username", null);
		EditText user = (EditText) findViewById(R.id.login_username);
		if(username != null){
			user.setText(username);
		}
	}

	protected void onResume() {
		super.onResume();
	}
	
	protected void onDestroy(){
		super.onDestroy();
	}
	/* End Activity stuff */
	
	/**
	 * Description: Commits the credentials into the preference manager and
	 * 	handles the errors associated with them.
	 */
	private void commitCredentials(){
		EditText text_view = (EditText) findViewById(R.id.login_username);
		String user_name = text_view.getText().toString();
		text_view = (EditText) findViewById(R.id.login_password);
		String password = text_view.getText().toString();

		//Remove the stupid keyboard from view. It's in the way!
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(text_view.getWindowToken(), 0);
		
		if(user_name != null && 
				password != null && 
				user_name.length() > 0 && 
				password.length() > 0) {
			
			boolean success = m_prefs.edit()
									 .putString("pandora_username", user_name)
									 .putString("pandora_password", password)
									 .commit();
			m_prefs.edit().putBoolean("pandora_one_flag", true).apply();
			if(success) {
				finish();
			}
			else{
				AlertDialog.Builder 
					alert_builder = new AlertDialog.Builder(PandoroidLogin.this);
				alert_builder.setMessage("Internal Error. Please Try Again");
				alert_builder.setPositiveButton("Retry", 
						new DialogInterface.OnClickListener() {						
					public void onClick(DialogInterface dialog, int which) {
						commitCredentials();
					}
				});
			}
		}
		else{
			CharSequence toasty_text = "I believe you're missing something.";
			int dur = Toast.LENGTH_LONG;
			Toast i_like_toast = Toast.makeText(PandoroidLogin.this, toasty_text, dur);
			i_like_toast.show(); //OMG it's shaped like a box with letters!
		}
	}	
	
	/**
	 * Description: Necessary to modify the behavior of the back button when 
	 * 	this activity is started.
	 */
	public void onBackPressed(){
		moveTaskToBack(true);
	}
	
	/**
	 * Description: An OnClickListener for the sign in button.
	 */
	private class SignInButtonClickListener implements OnClickListener{
		public void onClick(View viewParam) {
			commitCredentials();
		}
	}
}
