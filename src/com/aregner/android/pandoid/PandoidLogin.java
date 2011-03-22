package com.aregner.android.pandoid;

import android.app.Activity;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class PandoidLogin extends Activity {

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
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
						//finishActivity(PandoidPlayer.REQUIRE_LOGIN_CREDS);
					}
				}
			}
		});
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
		PandoidPlayer.dismissWaiting();
	}
}
