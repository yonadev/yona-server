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
		sleep $sleepTime
	done
	if [ $n -ge $iterations ] 
	then
		echo "Failed to get URL $1 within timeout ($duration seconds)"
		return 1
	fi

	return 0
}

# Temporarily use a different approach for Kubernetes
if [ "$1" == "k8s" ]
then
	duration=1200
	sleepTime=5
	iterations=$[$duration / $sleepTime]
	n=0
	until [ $n -ge $iterations ]
	do
		kubectl get pods -a --selector=job-name=${BUILD_NUMBER_TO_DEPLOY}-develop-liquibase-update -o jsonpath='{.items[*].status.phase}' | grep Succeeded && echo && break
		n=$[$n + 1]
		sleep $sleepTime
	done
	if [ $n -ge $iterations ] 
	then
		echo "Failed to get URL $1 within timeout ($duration seconds)"
		return 1
	fi
else
	echo "Waiting for the admin service to start"
	waitTillGetWorks http://127.0.0.1:8080/activityCategories/

	echo "Waiting for the analysis service to start"
	waitTillGetWorks http://127.0.0.1:8081/relevantSmoothwallCategories/

	echo "Waiting for the app service to start"
	waitTillGetWorks http://127.0.0.1/activityCategories/

	echo "Waiting for the batch service to start"
	waitTillGetWorks http://127.0.0.1:8083/scheduler/jobs/
fi
