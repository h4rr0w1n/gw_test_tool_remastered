@echo off
REM Run script for AMHS/SWIM test tool - Windows version
cd /d "%~dp0\.." || exit /b 1
call mvn exec:java -Dexec.mainClass="com.amhs.swim.test.Main"
