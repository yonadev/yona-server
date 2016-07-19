export yonatag=build-$1
docker-compose stop
docker-compose rm -f
docker-compose pull
docker-compose up -d
