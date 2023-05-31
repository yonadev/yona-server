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
call gradlew :dbinit:update -PrunList=updateMain
if ERRORLEVEL 1 goto error

echo.
echo Verifying the database schema
echo.
call gradlew :dbinit:bootRun
if ERRORLEVEL 1 goto error

call gradlew adminservice:build && start "Admin service" java -ea -Xdebug -Xrunjdwp:transport=dt_socket,address=8840,server=y,suspend=n -Dyona.testServer=true -Dyona.enableHibernateStatsAllowed=true -jar adminservice\build\libs\adminservice-0.0.8-SNAPSHOT-full.jar --server.port=8180 --management.server.port=9080 --spring.jpa.hibernate.ddl-auto=none
call gradlew analysisservice:build && start "Analysis service" java -ea -Xdebug -Xrunjdwp:transport=dt_socket,address=8841,server=y,suspend=n -Dyona.testServer=true -Dyona.enableHibernateStatsAllowed=true -jar analysisservice\build\libs\analysisservice-0.0.8-SNAPSHOT-full.jar --server.port=8181 --management.server.port=9081 --spring.jpa.hibernate.ddl-auto=none
call gradlew appservice:build && start "App service" java -ea -Xdebug -Xrunjdwp:transport=dt_socket,address=8842,server=y,suspend=n -Dyona.testServer=true -Dyona.enableHibernateStatsAllowed=true -jar appservice\build\libs\appservice-0.0.8-SNAPSHOT-full.jar --server.port=8182 --management.server.port=9082 --spring.jpa.hibernate.ddl-auto=none
call gradlew batchservice:build && start "Batch service" java -ea -Xdebug -Xrunjdwp:transport=dt_socket,address=8843,server=y,suspend=n -Dyona.testServer=true -Dyona.enableHibernateStatsAllowed=true -jar batchservice\build\libs\batchservice-0.0.8-SNAPSHOT-full.jar --server.port=8183 --management.server.port=9083 --spring.jpa.hibernate.ddl-auto=none

echo.
echo Wait until all services are started.
echo.
set CURLOPT=-s --retry-connrefused --retry-delay 3 --retry 20
curl %CURLOPT% http://localhost:8180/activityCategories/ > nul
if ERRORLEVEL 1 goto error
curl %CURLOPT% http://localhost:8181/relevantSmoothwallCategories/ > nul
if ERRORLEVEL 1 goto error
curl %CURLOPT% http://localhost:8182/activityCategories/ > nul
if ERRORLEVEL 1 goto error
curl %CURLOPT% http://localhost:8183/scheduler/jobs/ > nul
if ERRORLEVEL 1 goto error

if "%1"=="-keepDB" goto end

echo.
echo Load the activity categories
echo.
curl -f -X PUT --header "Content-Type: application/json" -d @dbinit/data/activityCategories.json http://localhost:8180/activityCategories/
if ERRORLEVEL 1 goto error

echo.
echo Load the Quartz jobs
echo.
curl -f -X PUT --header "Content-Type: application/json" -d @dbinit/data/QuartzOtherJobs.json http://localhost:8183/scheduler/jobs/OTHER/

echo.
echo Load the Quartz cron triggers
echo.
curl -f -X PUT --header "Content-Type: application/json" -d @dbinit/data/QuartzOtherCronTriggers.json http://localhost:8183/scheduler/triggers/cron/OTHER/

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
