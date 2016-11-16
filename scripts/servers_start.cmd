@echo off
 
call servers_stop.cmd

del YonaDB.*
 
call gradlew %1 build
if ERRORLEVEL 1 goto end

start "HSQL database" java -cp "%HSQLDB_HOME%/lib/sqltool.jar" org.hsqldb.Server -database.0 file:YonaDB -dbname.0 xdb

call gradlew :dbinit:liquibaseUpdate
if ERRORLEVEL 1 goto end

start "Admin service" cmd /c gradlew %1 :adminservice:bootRun
start "Analysis service" cmd /c gradlew %1 :analysisservice:bootRun
start "App service" cmd /c gradlew %1 :appservice:bootRun
start "Batch service" cmd /c gradlew %1 :batchservice:bootRun

set GRADLE_OPTS=
echo Wait until all services are started.
pause
curl -X PUT --header "Content-Type: application/json" -d @dbinit/data/activityCategories.json http://localhost:8080/activityCategories/
cmd /c gradlew --rerun-tasks :adminservice:intTest :analysisservice:intTest :appservice:intTest 

:end
