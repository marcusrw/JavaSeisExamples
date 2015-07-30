#!/usr/bin/bash

## temporary script for running sonarqube manually on OG0001

## First make sure SonarQube is running
ps -ef | grep sonar | grep qube
if [ $? == 0 ]; then
	echo "A running instance of SonarQube was detected."
else
	echo "SonarQube is not running."
	/etc/sonarqube/bin/linux-x86-64/sonar.sh start
	echo "Waiting 30 seconds for the server to start accepting http connections."
	sleep 30
fi

## Now run the runner on the current directory
/etc/sonar-runner/bin/sonar-runner &&

## Look for an open SonarQube window, and open a new one if you can't find one
## This will open a second window  if SonarQube is open in an inactive tab
## but it will always open a window if you're running on a desktop, and one
## isn't already open.
if wmctrl -l | grep -q "SonarQube"; then
        echo "SonarQube window is already open"
else
        echo "Opening SonarQube website"
        firefox -new-window OG0001:9000 &
fi

