export yonatag=build-$1
echo "Stopping Yona containers"
docker-compose stop

echo "Removing old containers"
docker-compose rm -f

echo "Updating the docker-compose.yml file"
rm -f docker-compose.yml
wget https://raw.githubusercontent.com/yonadev/yona-server/master/docker-compose/yona/docker-compose.yml

echo "Pulling new images"
docker-compose pull

echo "Starting the containers again"
docker-compose up -d
