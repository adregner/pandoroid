package com.aregner.android.pandoid;

import android.os.Bundle;
import android.preference.PreferenceActivity;

public class PandoidSettings extends PreferenceActivity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
    }
}