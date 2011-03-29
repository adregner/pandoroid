#!/bin/bash
# script to package this android application

PACKAGE_NAME=pandoroid #$(basename $(pwd))

tar cvjf "../$PACKAGE_NAME.tar.bz2" --exclude-backups --exclude="consoleTesting" --exclude="bin" * .classpath .project .bzr*
cp bin/"$(basename $(pwd))".apk "../$PACKAGE_NAME.apk"

scp "../$PACKAGE_NAME.apk" "../$PACKAGE_NAME.tar.bz2" doc/*.{html,png} aregner.com:public_html/android/"$PACKAGE_NAME"/
