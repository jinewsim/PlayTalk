#!/usr/bin/env bash
# 서버 컴파일 & 실행 (macOS / Linux).  사용법: ./run-server.sh [포트]
set -e
cd "$(dirname "$0")"
mkdir -p out
find src/main/java -name '*.java' > out/sources.txt
javac -encoding UTF-8 -d out/classes @out/sources.txt
java -cp out/classes:src/main/resources playtalk.server.ServerMain "$@"
