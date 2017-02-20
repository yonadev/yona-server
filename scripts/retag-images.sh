#!/bin/bash
set -e # Fail on error

export jenkinsUrl="https://yonadev.ci.cloudbees.com/job/build-and-test/job/master/lastSuccessfulBuild/api/xml?xpath=/workflowRun/action/buildsByBranchName/master/buildNumber/text()"
#Extra curl call to ensure we fail if curl fails
curl -f -s -H "Accept:application/json" "$jenkinsUrl"

export build=`curl -f -s -H "Accept:application/json" "$jenkinsUrl"`
for image in appservice adminservice batchservice analysisservice yona-mariadb-liquibase-update
do
	echo Tagging and pushing yonadev/$image:build-$build
	docker tag yonadev/$image:latest yonadev/$image:build-$build
	docker push yonadev/$image:build-$build
done
