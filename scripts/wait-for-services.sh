#!/bin/bash
set -e # Fail on error

function waitTillGetWorks() {
	duration=600
	sleepTime=5
	iterations=$[$duration / $sleepTime]
	n=0
	until [ $n -ge $iterations ]
	do
		curl -f $1 && echo && break
		n=$[$n + 1]
		sleep 5
	done
	if [ $n -ge $iterations ] 
	then
		echo Failed to get URL $1 within timeout ($duration seconds)
		return 1
	fi

	return 0
}

# Temporarily use different ports for k8s and Docker compose ports
if [ "$1" == "k8s" ]
then
ADMIN_SVC_PORT=31000
ANALYSIS_SVC_PORT=31001
APP_SVC_PORT=31002
BATCH_SVC_PORT=31003
else
ADMIN_SVC_PORT=8080
ANALYSIS_SVC_PORT=8081
APP_SVC_PORT=80
BATCH_SVC_PORT=8082
fi

echo "Waiting for the admin service to start"
waitTillGetWorks http://127.0.0.1:$ADMIN_SVC_PORT/activityCategories/

echo "Waiting for the analysis service to start"
waitTillGetWorks http://127.0.0.1:$ANALYSIS_SVC_PORT/relevantSmoothwallCategories/

echo "Waiting for the app service to start"
waitTillGetWorks http://127.0.0.1:$APP_SVC_PORT/activityCategories/

echo "Waiting for the batch service to start"
waitTillGetWorks http://127.0.0.1:$BATCH_SVC_PORT/scheduler/jobs/
