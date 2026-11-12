#!/usr/bin/env bash
# 클라이언트 컴파일 & 실행 (macOS / Linux). 여러 개 실행하면 여러 사용자로 테스트할 수 있다.
set -e
cd "$(dirname "$0")"
mkdir -p out
find src/main/java -name '*.java' > out/sources.txt
javac -encoding UTF-8 -d out/classes @out/sources.txt
java -cp out/classes:src/main/resources playtalk.client.ClientMain
