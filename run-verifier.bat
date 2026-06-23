@echo off
REM AMHS/SWIM Verifier - Run Executable JAR
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

echo [INFO] Starting AMHS/SWIM Verifier...
java -cp "amhs-swim-tool.jar" com.amhs.swim.test.verifier.Verifier %*
pause
