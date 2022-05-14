#!/bin/bash
set -e # Fail on error

_NAMESPACE=${NAMESPACE:-yona}
_INITIAL_WAIT_TIME=${INITIAL_WAIT_TIME:-1200}

function waitTillK8SInstanceWorks() {
	duration=${3:-1200}
	sleepTime=${4:-5}
	iterations=$[$duration / $sleepTime]
	n=0
	echo "Waiting for ${1}-${BUILD_NUMBER_TO_DEPLOY} in namespace ${_NAMESPACE} to become ${2}"
	until [ $n -ge $iterations ]
	do

	# Debug logging
	kubectl get pods --selector=app=${1} -n ${_NAMESPACE} -o=jsonpath='{range .items[*]}{.metadata.name}:{.status.phase}{"\n"}{end}'
	kubectl get pods --selector=app=${1} -n ${_NAMESPACE} -o=jsonpath='{range .items[*]}{.metadata.name}:{.status.phase}{"\n"}{end}' | grep -e "^${BUILD_NUMBER_TO_DEPLOY}.*-liquibase-update.*${2}"
	kubectl get pods --selector=app=${1} -n ${_NAMESPACE} -o jsonpath='{.items[*].status.phase}'
	kubectl get pods --selector=app=${1} -n ${_NAMESPACE} -o jsonpath='{.items[*].status.phase}' | grep ${2}

	if [ "$2" == "Succeeded" ]; then  #Hack to deal with Job differently
 		kubectl get pods --selector=app=${1} -n ${_NAMESPACE} -o=jsonpath='{range .items[*]}{.metadata.name}:{.status.phase}{"\n"}{end}' | grep -q -e "^${BUILD_NUMBER_TO_DEPLOY}.*-liquibase-update.*${2}" && echo -e "\n - Success\n" && break
	else
		kubectl get pods --selector=app=${1} -n ${_NAMESPACE} -o jsonpath='{.items[*].status.phase}' | grep -q ${2} && echo -e "\n - Success\n" && break
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

waitTillK8SInstanceWorks liquibase Succeeded $(_INITIAL_WAIT_TIME) 5
waitTillK8SInstanceWorks admin Running 60
waitTillK8SInstanceWorks analysis Running 60
waitTillK8SInstanceWorks app Running 60
waitTillK8SInstanceWorks batch Running 60
