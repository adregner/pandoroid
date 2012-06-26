Pandoroid Radio
===============
Status: Functional!

This is eventually going to be a full-featured [Pandora Radio](http://www.pandora.com/) client for Android-based devices.  Due to failings in the proprietary application provided by Pandora Radio themselves (the most glaring being audio quality issues), this application is being designed with three focuses in mind: _stability_, _intuitiveness_, and _configurability_ (in that order).

Check out the project's home page (http://aregner.com/android/pandoroid/) for details.

At the moment, the application is just about completely functional, with a few manageable bugs. Stability wise, things aren't looking so good. For any difficulties, the general rule of thumb is to restart the app. Feedback is welcome and encouraged.

This version is heavily modified by Tortel, and based on the fork by [dylanPowers](https://github.com/dylanPowers/pandoroid), who implemented the Pandora JSON API (Thanks!). It has a different package name, so it can be installed along side other Pandoroid fork.

## Note about Using the Code
This version uses the [ActionBarSherlock] (http://actionbarsherlock.com/) compatibility library to give the Android 4.0 styling to the older Android versions.  
To edit and compile the source, check out the [usage] (http://actionbarsherlock.com/usage.html) page, under Including In Your Project.  
You will need the latest ADT, have the latest Android APIs installed (Currently version 15 (4.0.4)), and make sure the build target is API 15.
