#!/usr/bin/env bash

function killListeningProcess() {
    lsof -i:$1 -sTCP:LISTEN -t | xargs kill -9
}

killListeningProcess 9001
killListeningProcess 8080
killListeningProcess 8081
killListeningProcess 8082
