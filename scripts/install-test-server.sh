#!/bin/bash
set -e # Fail on error
my_dir="$(dirname "$0")"

export yonatag=latest
export yona_db_user_name=$1
export yona_db_password=$2
export yona_db_url=$3
export resources_source_folder=$4

# Go to the home of the root user
pushd /root/docker-compose/yona

echo "Stopping all Yona Docker containers"
docker-compose stop

# Go to the MariaDB directory
pushd ../mariadb

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
docker run --rm --network yonanet --link mariadb:mariadb -e TARGETS=mariadb:3306 waisbrot/wait

echo "Loading the Yona database schema"
docker run --rm -i --network yonanet --link mariadb:yonadbserver -e USER=root -e PASSWORD=root -e URL=jdbc:mariadb://yonadbserver:3306/yona  yonadev/yona-mariadb-liquibase-update:latest

# Return to Yona Docker compose folder
popd

echo "Removing the Yona Docker containers"
docker-compose rm -f -v

echo "Updating the docker-compose.yml file"
wget -O docker-compose.yml https://raw.githubusercontent.com/yonadev/yona-server/master/docker-compose/yona/docker-compose.yml

echo "Pulling all images"
docker-compose pull

echo "Generating database connection environment file"
cat << EOF > db_settings.env
YONA_DB_USER_NAME=$yona_db_user_name
YONA_DB_PASSWORD=$yona_db_password
YONA_DB_URL=$yona_db_url
EOF

echo "Copying the resouorces"
"$my_dir/copy-resources.sh" master $resources_source_folder

echo "Starting the containers"
docker-compose up -d

# Return to workspace root folder
popd

echo "Waiting for the services to start"
"$my_dir/wait-for-services.sh"

echo "Loading the activity categories"
curl https://raw.githubusercontent.com/yonadev/yona-server/master/dbinit/data/activityCategories.json | curl -X PUT http://localhost:8080/activityCategories/ -d @- --header "Content-Type: application/json"

echo "Loading the Quartz jobs"
curl https://raw.githubusercontent.com/yonadev/yona-server/master/dbinit/data/QuartzOtherJobs.json | curl -X PUT http://localhost:8083/scheduler/jobs/OTHER/ -d @- --header "Content-Type: application/json"

echo "Loading the Quartz cron triggers"
curl https://raw.githubusercontent.com/yonadev/yona-server/master/dbinit/data/QuartzOtherCronTriggers.json | curl -X PUT http://localhost:8083/scheduler/triggers/cron/OTHER/ -d @- --header "Content-Type: application/json"
