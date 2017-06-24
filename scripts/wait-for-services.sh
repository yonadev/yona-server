#!/bin/bash
set -e # Fail on error

function waitTillGetWorks() {
	n=0
	until [ $n -ge 24 ]
	do
		curl -f $1 && echo && break
		n=$[$n+1]
		sleep 5
	done
	if [ $n -ge 24 ] 
	then
		echo Failed to get URL $1 within timeout
		return 1
	fi

	return 0
}

# Temporarily use different ports for k8s and Docker compose ports
if [ "$1" == "k8s" ]
then
ADMIN_SVC_PORT=31001
ANALYSIS_SVC_PORT=31002
APP_SVC_PORT=31003
BATCH_SVC_PORT=31004
else
ADMIN_SVC_PORT=8080
ANALYSIS_SVC_PORT=8081
APP_SVC_PORT=80
BATCH_SVC_PORT=8082
fi

echo "Waiting for the admin service to start"
waitTillGetWorks http://localhost:$ADMIN_SVC_PORT/activityCategories/

echo "Waiting for the analysis service to start"
waitTillGetWorks http://localhost:$ANALYSIS_SVC_PORT/relevantSmoothwallCategories/

echo "Waiting for the app service to start"
waitTillGetWorks http://localhost:$APP_SVC_PORT/activityCategories/

echo "Waiting for the batch service to start"
waitTillGetWorks http://localhost:$BATCH_SVC_PORT/scheduler/jobs/
