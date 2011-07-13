#!/bin/bash
# script to package this android application

PACKAGE_NAME=pandoroid #$(basename $(pwd))

# use this to fail if we try to publish without commited changes
bzr dpush --strict --no-rebase || exit 1

# bundle it all up
tar cvjf "../$PACKAGE_NAME.tar.bz2" --exclude-backups --exclude="consoleTesting" --exclude="bin" * .classpath .project .bzr*
cp bin/"$(basename $(pwd))".apk "../$PACKAGE_NAME.apk"

# send it to the project's main site
scp "../$PACKAGE_NAME.apk" "../$PACKAGE_NAME.tar.bz2" doc/*.{html,png,rss} aregner.com:public_html/android/"$PACKAGE_NAME"/
