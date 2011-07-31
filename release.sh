#!/bin/bash
# script to package this android application

PACKAGE_NAME=pandoroid #$(basename $(pwd))

git push
git push github 2> /dev/null

# bundle it all up
tar cvjf "../$PACKAGE_NAME.tar.bz2" --exclude-backups --exclude="consoleTesting" --exclude="bin" * .classpath .project .git*
cp bin/"$(basename $(pwd))".apk "../$PACKAGE_NAME.apk"

# send it to the project's main site
scp "../$PACKAGE_NAME.apk" "../$PACKAGE_NAME.tar.bz2" doc/*.{html,png,rss} aregner.com:public_html/android/"$PACKAGE_NAME"/
