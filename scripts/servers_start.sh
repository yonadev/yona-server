#!/usr/bin/env bash
#*******************************************************************************
# Copyright (c) 2016 Stichting Yona Foundation
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.
#*******************************************************************************

./servers_stop.sh

function waitTillPortIsListenedTo() {
    while ! nc -z localhost $1; do sleep 2; done
}
 
./gradlew $@ build

echo "\nRecreating Yona database\n"
mysql --user=$YONA_DB_USER_NAME --password=$YONA_DB_PASSWORD < scripts/recreateYonaDB.sql

./gradlew :dbinit:liquibaseUpdate

./gradlew $@ :adminservice:bootRun > /dev/null 2>&1 &
./gradlew $@ :analysisservice:bootRun > /dev/null 2>&1 &
./gradlew $@ :appservice:bootRun > /dev/null 2>&1 &
./gradlew $@ :batchservice:bootRun > /dev/null 2>&1 &
waitTillPortIsListenedTo 8080
waitTillPortIsListenedTo 8081
waitTillPortIsListenedTo 8082
waitTillPortIsListenedTo 8083

export GRADLE_OPTS=
curl -X PUT --header "Content-Type: application/json" -d @dbinit/data/activityCategories.json http://localhost:8080/activityCategories/
./gradlew --rerun-tasks :adminservice:intTest :analysisservice:intTest :appservice:intTest 
