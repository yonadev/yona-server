#!/bin/bash
set -e # Fail on error

export build=$1
for image in appservice adminservice batchservice analysisservice yona-mariadb-liquibase-update
do
	echo Tagging and pushing yonadev/$image:napi-build-$build
	docker tag yonadev/$image:latest yonadev/$image:napi-build-$build
	docker push yonadev/$image:napi-build-$build
done
