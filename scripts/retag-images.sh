docker tag yonadev/appservice:0.0.8-SNAPSHOT yonadev/appservice:build-$1
docker tag yonadev/adminservice:0.0.8-SNAPSHOT yonadev/adminservice:build-$1
docker tag yonadev/batchservice:0.0.8-SNAPSHOT yonadev/batchservice:build-$1
docker tag yonadev/analysisservice:0.0.8-SNAPSHOT yonadev/analysisservice:build-$1
docker push yonadev/appservice:build-$1
docker push yonadev/adminservice:build-$1
docker push yonadev/batchservice:build-$1
docker push yonadev/analysisservice:build-$1
