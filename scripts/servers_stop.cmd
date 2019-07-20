@echo off
 
rem Kill all processes listening to the service ports.
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:9001.*LISTENING') DO TaskKill.exe /F /PID %%P
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8180.*LISTENING') DO TaskKill.exe /F /PID %%P
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8181.*LISTENING') DO TaskKill.exe /F /PID %%P
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8182.*LISTENING') DO TaskKill.exe /F /PID %%P
FOR /F "tokens=5 delims= " %%P IN ('netstat -a -n -o ^| findstr 0.0.0.0:8183.*LISTENING') DO TaskKill.exe /F /PID %%P
