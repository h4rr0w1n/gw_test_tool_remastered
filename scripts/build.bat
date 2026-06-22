@echo off
setlocal enabledelayedexpansion
REM Build script for AMHS/SWIM test tool - Windows version

echo AMHS/SWIM Gateway Test Tool - Build Script
echo ==========================================
echo.

REM Ensure we're in the project root
cd /d "%~dp0\.." || exit /b 1

REM Check for Java
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed or not in PATH.
    echo Please install Java JDK 11+ and add it to your PATH.
    pause
    exit /b 1
)

REM Check for Maven
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: Maven is not installed or not in PATH.
    echo Please install Apache Maven and add it to your PATH.
    pause
    exit /b 1
)

echo Checking for required dependencies...
if not exist "lib" (
    echo Creating lib directory...
    mkdir lib
)

echo Downloading Solace JCSMP library if not present...
if not exist "lib\sol-jcsmp-10.20.0.jar" (
    echo Downloading from Maven Central...
    powershell -Command "Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/solacesystems/sol-jcsmp/10.20.0/sol-jcsmp-10.20.0.jar' -OutFile 'lib\sol-jcsmp-10.20.0.jar'"
    if exist "lib\sol-jcsmp-10.20.0.jar" (
        echo Successfully downloaded Solace JCSMP library.
    ) else (
        echo Warning: Failed to download Solace JCSMP library automatically.
        echo Please download it manually from Maven Central and place it in the lib folder.
    )
) else (
    echo Solace JCSMP library already exists.
)

echo.
echo Building project with Maven...
echo.

call mvn clean compile

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ==========================================
    echo BUILD SUCCESSFUL
    echo ==========================================
    echo.
    echo You can now run the tool using: run_tool.bat
) else (
    echo.
    echo ==========================================
    echo BUILD FAILED
    echo ==========================================
    echo Please check the error messages above.
    pause
    exit /b 1
)
