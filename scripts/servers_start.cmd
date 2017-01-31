@echo off
 
call servers_stop.cmd

call gradlew %1 build
if ERRORLEVEL 1 goto end

echo.
echo Recreating Yona database
echo.
call mysql --user=%YONA_DB_USER_NAME% --password=%YONA_DB_PASSWORD% < scripts\recreateYonaDB.sql
if ERRORLEVEL 1 goto end

call gradlew :dbinit:liquibaseUpdate
if ERRORLEVEL 1 goto end

echo Verifying the database schema
call gradlew :dbinit:bootRun
if ERRORLEVEL 1 goto end

start "Admin service" java -jar adminservice\build\libs\adminservice-0.0.8-SNAPSHOT-full.jar --server.port=8080 --management.port=9080 --spring.jpa.hibernate.ddl-auto=none
start "Analysis service" java -jar analysisservice\build\libs\analysisservice-0.0.8-SNAPSHOT-full.jar --server.port=8081 --management.port=9081 --spring.jpa.hibernate.ddl-auto=none
start "App service" java -jar appservice\build\libs\appservice-0.0.8-SNAPSHOT-full.jar --server.port=8082 --management.port=9082 --spring.jpa.hibernate.ddl-auto=none
start "Batch service" java -jar batchservice\build\libs\batchservice-0.0.8-SNAPSHOT-full.jar --server.port=8083 --management.port=9083 --spring.jpa.hibernate.ddl-auto=none

set GRADLE_OPTS=
echo Wait until all services are started.
pause
curl -X PUT --header "Content-Type: application/json" -d @dbinit/data/activityCategories.json http://localhost:8080/activityCategories/
cmd /c gradlew --rerun-tasks :adminservice:intTest :analysisservice:intTest :appservice:intTest 

:end
