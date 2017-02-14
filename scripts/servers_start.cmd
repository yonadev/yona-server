@echo off
 
call servers_stop.cmd

call gradlew build
if ERRORLEVEL 1 goto error

if "%1"=="-keepDB" goto updateDB
echo.
echo Recreating Yona database
echo.
call mysql --user=%YONA_DB_USER_NAME% --password=%YONA_DB_PASSWORD% < scripts\recreateYonaDB.sql
if ERRORLEVEL 1 goto error

:updateDB
call gradlew :dbinit:liquibaseUpdate
if ERRORLEVEL 1 goto error

echo.
echo Verifying the database schema
echo.
call gradlew :dbinit:bootRun
if ERRORLEVEL 1 goto error

call gradlew adminservice:build && start "Admin service" java -Xdebug -Xrunjdwp:transport=dt_socket,address=8840,server=y,suspend=n -jar adminservice\build\libs\adminservice-0.0.8-SNAPSHOT-full.jar --server.port=8080 --management.port=9080 --spring.jpa.hibernate.ddl-auto=none
call gradlew analysisservice:build && start "Analysis service" java -Xdebug -Xrunjdwp:transport=dt_socket,address=8841,server=y,suspend=n -jar analysisservice\build\libs\analysisservice-0.0.8-SNAPSHOT-full.jar --server.port=8081 --management.port=9081 --spring.jpa.hibernate.ddl-auto=none
call gradlew appservice:build && start "App service" java -Xdebug -Xrunjdwp:transport=dt_socket,address=8842,server=y,suspend=n -jar appservice\build\libs\appservice-0.0.8-SNAPSHOT-full.jar --server.port=8082 --management.port=9082 --spring.jpa.hibernate.ddl-auto=none
call gradlew batchservice:build && start "Batch service" java -Xdebug -Xrunjdwp:transport=dt_socket,address=8843,server=y,suspend=n -jar batchservice\build\libs\batchservice-0.0.8-SNAPSHOT-full.jar --server.port=8083 --management.port=9083 --spring.jpa.hibernate.ddl-auto=none

echo.
echo Wait until all services are started.
echo.
set CURLOPT=-s --retry-connrefused --retry-delay 3 --retry 20
curl %CURLOPT% http://localhost:8080/activityCategories/ > nul
if ERRORLEVEL 1 goto error
curl %CURLOPT% http://localhost:8081/relevantSmoothwallCategories/ > nul
if ERRORLEVEL 1 goto error
curl %CURLOPT% http://localhost:8082/activityCategories/ > nul
if ERRORLEVEL 1 goto error

echo.
echo Load the activity categories
echo.
curl -f -X PUT --header "Content-Type: application/json" -d @dbinit/data/activityCategories.json http://localhost:8080/activityCategories/
if ERRORLEVEL 1 goto error

echo.
echo.
echo Start the integration tests
echo.
cmd /c gradlew --rerun-tasks :adminservice:intTest :analysisservice:intTest :appservice:intTest 

goto end

:error
echo.
echo *** ERROR OCCURRED

:end
