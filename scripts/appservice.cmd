@echo off

FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8082.*LISTENING') DO TaskKill.exe /F /PID %%P
start cmd /c gradlew -Pdebug.all=true :appservice:run