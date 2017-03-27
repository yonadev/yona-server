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

echo "Waiting for the admin service to start"
waitTillGetWorks http://localhost:8080/activityCategories/

echo "Waiting for the analysis service to start"
waitTillGetWorks http://localhost:8081/relevantSmoothwallCategories/

echo "Waiting for the app service to start"
waitTillGetWorks http://localhost/activityCategories/

echo "Waiting for the batch service to start"
waitTillGetWorks http://localhost:8083/scheduler/jobs/
