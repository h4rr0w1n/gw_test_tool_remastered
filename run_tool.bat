@echo off
REM AMHS/SWIM Gateway Test Tool Runner Script for Windows

REM Ensure we're in the project root
cd /d "%~dp0" || exit /b 1

REM Check for Java
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed or not in PATH.
    echo Please install Java JDK 11+ and add it to your PATH.
    pause
    exit /b 1
)

REM Check if target directory exists, if not try to build
if not exist "target\classes" (
    echo Info: 'target\classes' not found. Attempting to compile...
    call mvn compile
    if %ERRORLEVEL% NEQ 0 (
        echo Error: Compilation failed. Please ensure Maven is installed and configured.
        pause
        exit /b 1
    )
)

echo Setting up classpath...

REM Get Maven dependencies classpath
for /f "delims=" %%i in ('mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout') do set CP=%%i

REM Add project classes and local lib jars
set CP=target\classes;lib\*;%CP%

echo Starting AMHS/SWIM Gateway Test Tool...
java -cp "%CP%" com.amhs.swim.test.Main %*

REM Keep window open if run directly (not from command line)
if "%~1"=="" pause
