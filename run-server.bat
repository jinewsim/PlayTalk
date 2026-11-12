@echo off
chcp 65001 > nul
cd /d %~dp0
if not exist out mkdir out
dir /s /b src\main\java\*.java > out\sources.txt
javac -encoding UTF-8 -d out\classes @out\sources.txt || exit /b 1
java -Dstdout.encoding=UTF-8 -cp out\classes;src\main\resources playtalk.server.ServerMain %*
