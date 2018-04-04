#!/bin/bash
set -e # Fail on error

_NAMESPACE=${NAMESPACE:-yona}

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

function waitTillK8SInstanceWorks() {
	duration=${3:-1200}
	sleepTime=${4:-5}
	iterations=$[$duration / $sleepTime]
	n=0
  echo "Waiting for ${1}-${BUILD_NUMBER_TO_DEPLOY} to become ${2}"
	until [ $n -ge $iterations ]
	do
    if [ "$2" == "Succeeded" ]; then  #Hack because we can't have labels in deployments that change over releases
      kubectl get pods -a --selector=app=${1},build=${BUILD_NUMBER_TO_DEPLOY} -n ${_NAMESPACE} -o jsonpath='{.items[*].status.phase}' | grep -q ${2} && echo -e "\n - Success\n" && break
    else
      kubectl get pods -a --selector=app=${1} -n ${_NAMESPACE} -o jsonpath='{.items[*].status.phase}' | grep -q ${2} && echo -e "\n - Success\n" && break
    fi
    echo -n '.'
		n=$[$n + 1]
		sleep $sleepTime
	done
	if [ $n -ge $iterations ]
	then
		echo "Container ${BUILD_NUMBER_TO_DEPLOY}-${1} failed to become ${2} within timeout ($duration seconds)"
		return 1
	fi
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
elif [ "$1" == "k8snew" ]
then
	waitTillK8SInstanceWorks liquibase Succeeded 1200 5
	waitTillK8SInstanceWorks admin Running 60
	waitTillK8SInstanceWorks analysis Running 60
	waitTillK8SInstanceWorks app Running 60
	waitTillK8SInstanceWorks batch Running 60
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
