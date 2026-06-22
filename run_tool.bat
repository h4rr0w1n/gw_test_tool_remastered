@echo off
setlocal enabledelayedexpansion
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

REM Check if target/classes exists, if not try to build
if not exist "target\classes\com\amhs\swim\test\Main.class" (
    echo Info: Main class not found. Attempting to compile...
    where mvn >nul 2>nul
    if %ERRORLEVEL% NEQ 0 (
        echo Error: Maven is not installed or not in PATH.
        echo Please install Maven and add it to your PATH, or run scripts\build.bat first.
        pause
        exit /b 1
    )
    echo Running Maven compile...
    call mvn clean compile
    if %ERRORLEVEL% NEQ 0 (
        echo Error: Compilation failed. Please ensure Maven is installed and configured.
        pause
        exit /b 1
    )
    echo Compilation successful.
)

echo Setting up classpath...

REM Build classpath manually to avoid issues
set CP=target\classes

REM Add all JARs from lib directory
if exist "lib" (
    for %%f in (lib\*.jar) do (
        set CP=!CP!;%%f
    )
)

REM Check if Maven is available for getting additional dependencies
where mvn >nul 2>nul
if %ERRORLEVEL% EQU 0 (
    echo Getting Maven dependencies...
    for /f "delims=" %%i in ('mvn -q dependency:build-classpath -Dmdep.outputFile=CON 2^>nul') do set MAVEN_CP=%%i
    if defined MAVEN_CP (
        set CP=%CP%;%MAVEN_CP%
    )
)

echo Classpath configured.
echo Starting AMHS/SWIM Gateway Test Tool...
echo.

java -cp "%CP%" com.amhs.swim.test.Main %*

REM Capture exit code
set EXIT_CODE=%ERRORLEVEL%

REM Keep window open if run directly (not from command line with arguments)
if "%~1"=="" (
    echo.
    echo Exit code: %EXIT_CODE%
    pause
)

exit /b %EXIT_CODE%
