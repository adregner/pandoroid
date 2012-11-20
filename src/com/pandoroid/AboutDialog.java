/* Pandoroid Radio - open source pandora.com client for android
 * Copyright (C) 2011  Andrew Regner <andrew@aregner.com>
 * Copyright (C) 2012  Scott Warner <Tortel1210@gmail.com>
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

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.widget.TextView;
import com.actionbarsherlock.app.SherlockActivity;
import com.pandoroid.R;

public class AboutDialog extends SherlockActivity {

	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);
		String version_name = "";
		int build_num = 0;
		try {
			version_name = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
			build_num = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
		} catch (NameNotFoundException e) {}
		
		String version_text = "Version " + version_name + " (" + build_num + ")";
		SpannableString about_text = new SpannableString(getString(R.string.about) + 
														 " (https://github.com/dylanPowers/pandoroid/wiki).");
		int url_start = about_text.length() - 47;
		int url_end = about_text.length() - 2;
		about_text.setSpan(new URLSpan("https://github.com/dylanPowers/pandoroid/wiki"), 
					       url_start, url_end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
		((TextView) findViewById(R.id.version_text)).setText(version_text);
		TextView about_view = (TextView) findViewById(R.id.about_text);
		about_view.setText(about_text);
		about_view.setMovementMethod(LinkMovementMethod.getInstance());
	}
}
