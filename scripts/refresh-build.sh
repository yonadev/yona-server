#!/bin/bash
set -e # Fail on error
my_dir="$(dirname "$0")"

export COMPOSE_PROJECT_NAME=yona
export yonatag=build-$1
export yona_db_user_name=$2
export yona_db_password=$3
export yona_db_url=$4
export config_file=$5
export backup_dir=$6

echo "Generating database connection environment file"
cat << EOF > db_settings.env
YONA_DB_USER_NAME=$yona_db_user_name
YONA_DB_PASSWORD=$yona_db_password
YONA_DB_URL=$yona_db_url
EOF

echo "Pulling new images, based on old compose file."
echo "As the compose file rarely changes, this normally pulls everything needed"
echo "The server is still running, so this reduces downtime"
[[ -f docker-compose.yml ]] || wget https://raw.githubusercontent.com/yonadev/yona-server/releases/tag/$yonatag/docker-compose/yona/docker-compose.yml
docker-compose pull
docker pull yonadev/yona-mariadb-liquibase-update:$yonatag

echo "Stopping Yona containers"
docker-compose stop

echo "Backing up the database"
docker exec mariadb sh -c "exec mysqldump --databases yona -u$yona_db_user_name -p$yona_db_password"  | gzip -c > $backup_dir/yonadb-before-$yonatag.sql.gz

echo "Removing old containers with their anonymous volumes"
docker-compose rm -f -v

echo "Updating the docker-compose.yml file"
rm -f docker-compose.yml
wget https://raw.githubusercontent.com/yonadev/yona-server/releases/tag/$yonatag/docker-compose/yona/docker-compose.yml

echo "Pulling new images"
docker-compose pull

echo "Updating the database schema"
docker run -i --rm --network yonanet --link mariadb:yonadbserver -e USER=$yona_db_user_name -e PASSWORD=$yona_db_password -e URL=$yona_db_url yonadev/yona-mariadb-liquibase-update:$yonatag

echo "Copying the config file"
[[ -d config ]] || mkdir config
cp $config_file config/application.properties

echo "Starting the containers again"
docker-compose up -d

echo "Waiting for the services to start"
"$my_dir/wait-for-services.sh"

echo "Loading the activity categories"
curl https://raw.githubusercontent.com/yonadev/yona-server/releases/tag/$yonatag/dbinit/data/productionActivityCategories.json | curl -X PUT http://localhost:8080/activityCategories/ -d @- --header "Content-Type: application/json"
