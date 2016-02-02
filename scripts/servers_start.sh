#!/usr/bin/env bash

./servers_stop.sh

rm -rf YonaDB.*

function waitTillPortIsListenedTo() {
    while ! nc -z localhost $1; do sleep 2; done
}
 
./gradlew $@ build :dbinit:bootRun

java -cp "$HSQLDB_HOME/lib/sqltool.jar" org.hsqldb.Server -database.0 file:YonaDB -dbname.0 xdb > /dev/null 2>&1 &
waitTillPortIsListenedTo 9001
./gradlew $@ :adminservice:bootRun > /dev/null 2>&1 &
waitTillPortIsListenedTo 8080
./gradlew $@ :analysisservice:bootRun > /dev/null 2>&1 &
waitTillPortIsListenedTo 8081
./gradlew $@ :appservice:bootRun > /dev/null 2>&1 &
waitTillPortIsListenedTo 8082

export GRADLE_OPTS=
./gradlew --rerun-tasks :adminservice:intTest :analysisservice:intTest :appservice:intTest 
