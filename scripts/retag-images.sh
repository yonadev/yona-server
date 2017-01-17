docker tag yonadev/appservice:latest yonadev/appservice:build-$1
docker tag yonadev/adminservice:latest yonadev/adminservice:build-$1
docker tag yonadev/batchservice:latest yonadev/batchservice:build-$1
docker tag yonadev/analysisservice:latest yonadev/analysisservice:build-$1
docker tag yonadev/yona-mariadb-liquibase-update:latest yonadev/yona-mariadb-liquibase-update:build-$1
docker push yonadev/appservice:build-$1
docker push yonadev/adminservice:build-$1
docker push yonadev/batchservice:build-$1
docker push yonadev/analysisservice:build-$1
docker push yonadev/yona-mariadb-liquibase-update:build-$1
