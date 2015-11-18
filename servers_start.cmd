@echo off
 
rem Kill all processes listening to the service ports.
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:9001.*LISTENING') DO TaskKill.exe /F /PID %%P
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8080.*LISTENING') DO TaskKill.exe /F /PID %%P
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8081.*LISTENING') DO TaskKill.exe /F /PID %%P
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8082.*LISTENING') DO TaskKill.exe /F /PID %%P
 
del YonaDB.*
 
start java -cp "%HSQLDB_HOME%/lib/sqltool.jar" org.hsqldb.Server -database.0 file:YonaDB -dbname.0 xdb
start cmd /c gradlew %1 :adminservice:run
start cmd /c gradlew %1 :analysisservice:run
start cmd /c gradlew %1 :appservice:run

set GRADLE_OPTS=
echo Wait until all services are started.
pause
cmd /c gradlew --rerun-tasks :adminservice:intTest :analysisservice:intTest :appservice:intTest 

