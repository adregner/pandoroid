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
import com.aregner.android.pandoroid.R;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class PandoroidLogin extends SherlockActivity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		setTheme(R.style.Theme_Sherlock);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);

		((Button)findViewById(R.id.login_button)).setOnClickListener(new OnClickListener() {
			public void onClick(View viewParam) {
				String sUserName = ((EditText)findViewById(R.id.login_username)).getText().toString();
				String sPassword = ((EditText)findViewById(R.id.login_password)).getText().toString();

				// this just catches the error if the program cant locate the GUI stuff
				if(sUserName != null && sPassword != null && sUserName.length() > 1 && sPassword.length() > 1) {
					boolean success = PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
						.putString("pandora_username", sUserName)
						.putString("pandora_password", sPassword)
						.commit();

					if(success) {
						setResult(RESULT_OK);
						finish();
						//finishActivityFromChild(child, PandoidPlayer.REQUIRE_LOGIN_CREDS);
						//finishActivity(PandoroidPlayer.REQUIRE_LOGIN_CREDS);
					}
				}
			}
		});
	}

	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.player_menu, menu);
		return true;
	}
	*/

	@Override
	protected void onResume() {
		super.onResume();
		PandoroidPlayer.dismissWaiting();
	}
}
