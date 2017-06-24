#!/bin/bash
set -e # Fail on error

# Go to the MariaDB directory
pushd /root/docker-compose/mariadb

echo "Stopping MariaDB"
docker-compose stop

echo "Removing the old database"
docker-compose rm -f -v

echo "Updating the MariaDB docker-compose.yml file"
wget -O docker-compose.yml https://raw.githubusercontent.com/yonadev/yona-server/master/docker-compose/mariadb/docker-compose.yml

echo "Pulling latest MariaDB image"
docker-compose pull

echo "Starting MariaDB"
docker-compose up -d

echo "Waiting for MariaDB"
docker run --rm --network yonanet --link mariadb:mariadb -e TARGETS=mariadb:3306 -e TIMEOUT=60 waisbrot/wait

# Return to Yona Docker compose folder
popd
