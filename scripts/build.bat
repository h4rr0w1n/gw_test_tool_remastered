@echo off
REM Build script for AMHS/SWIM test tool - Windows version
cd /d "%~dp0\.." || exit /b 1
call mvn clean install
