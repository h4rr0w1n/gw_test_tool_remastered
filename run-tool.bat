@echo off
REM AMHS/SWIM Gateway Test Tool - Run Executable JAR
REM Uses the pre-built JAR file (no compilation needed)

setlocal
cd /d "%~dp0"

REM Check if the JAR exists
if not exist "amhs-swim-tool.jar" (
    echo [ERROR] Executable JAR not found!
    echo Please build first by running: build.bat
    echo Or compile using: mvn clean package
    pause
    exit /b 1
)

echo [INFO] Starting AMHS/SWIM Gateway Test Tool...
java -jar "amhs-swim-tool.jar"
pause
