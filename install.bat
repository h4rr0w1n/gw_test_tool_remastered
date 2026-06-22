@echo off
REM AMHS/SWIM Gateway Test Tool Installation Script for Windows
REM This script installs required dependencies and builds the project.

setlocal enabledelayedexpansion

echo Starting AMHS/SWIM Gateway Test Tool Installation...

REM Ensure we're in the project root
cd /d "%~dp0" || exit /b 1

REM Colors are not available in Windows CMD, using plain text

REM 1. Check for Java
echo Checking for Java Development Kit (JDK)...
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Java not found. Please install Java JDK 11 or higher.
    echo Download from: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
) else (
    for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
        set JAVA_VERSION=%%g
    )
    echo Java is already installed (!JAVA_VERSION!).
)

REM 2. Check for Maven
echo Checking for Apache Maven...
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Maven not found. Please install Apache Maven.
    echo Download from: https://maven.apache.org/download.cgi
    echo Or install via chocolatey: choco install maven
    pause
    exit /b 1
) else (
    echo Maven is already installed.
)

REM 3. Setup lib directory
set LIB_DIR=lib
echo Setting up dependencies in '%LIB_DIR%'...
if not exist "%LIB_DIR%" (
    mkdir "%LIB_DIR%"
    echo Created lib\ directory.
) else (
    echo lib\ directory already exists.
)

REM 4. Download/Stub JARs
echo Locating Dependencies in '%LIB_DIR%'...

REM 4.1 Solace JCSMP
set SOLACE_VERSION=10.20.0
set SOLACE_JAR=%LIB_DIR%\sol-jcsmp-%SOLACE_VERSION%.jar
if not exist "%SOLACE_JAR%" (
    echo Downloading Solace JCSMP JAR v%SOLACE_VERSION% from Maven Central...
    REM Use PowerShell for downloading
    powershell -Command "& {Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/solacesystems/sol-jcsmp/%SOLACE_VERSION%/sol-jcsmp-%SOLACE_VERSION%.jar' -OutFile '%SOLACE_JAR%'}"
    if exist "%SOLACE_JAR%" (
        echo Successfully downloaded 'sol-jcsmp-%SOLACE_VERSION%.jar'.
    ) else (
        echo Failed to download Solace JAR. Creating stub...
        call :createJarStub "%SOLACE_JAR%" "Solace JCSMP"
    )
) else (
    echo Solace JAR found.
)

REM Create a minimal JAR stub function
:createJarStub
    echo Creating build-time stub for %~2...
    echo PK > "%~1"
    exit /b 0

REM 5. Build the project
echo Executing Maven build...
call mvn clean install
if %ERRORLEVEL% NEQ 0 (
    echo Maven build failed. Please check the error messages above.
    pause
    exit /b 1
)

echo.
echo Installation workflow complete.
echo You can now run the tool using run_tool.bat
pause
