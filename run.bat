@echo off
setlocal enabledelayedexpansion

:: ============================================================================
:: AMHS/SWIM Gateway Test Tool - Universal Run Script for Windows and Linux
:: ============================================================================
:: This script:
::   1. Checks for Java, Maven, and Python
::   2. Downloads required dependencies (Solace JCSMP) if missing
::   3. Compiles the project if needed
::   4. Runs the test tool with proper classpath OR runs the verifier
:: ============================================================================

:: Detect OS and set appropriate path separator
set "PATH_SEP=;"
set "SCRIPT_DIR=%~dp0"
set "IS_WINDOWS=true"

:: Check if running under WSL or Git Bash on Windows
if not defined IS_WINDOWS (
    set "PATH_SEP=:"
    set "IS_WINDOWS=false"
)

cd /d "%~dp0" || exit /b 1

:: Check for verifier mode
if "%~1"=="--verifier" (
    goto :RUN_VERIFIER
)

echo ============================================================================
echo AMHS/SWIM Gateway Test Tool
echo ============================================================================
echo.

:: ----------------------------------------------------------------------------
:: 1. Check for Java
:: ----------------------------------------------------------------------------
where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java is not installed or not in PATH.
    echo Please install Java JDK 11+ and add it to your PATH.
    echo Download from: https://adoptium.net/ or https://www.oracle.com/java/technologies/downloads/
    pause
    exit /b 1
)

for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VERSION=%%g"
)
echo [OK] Java found: %JAVA_VERSION%

:: ----------------------------------------------------------------------------
:: 2. Check for Maven
:: ----------------------------------------------------------------------------
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven is not installed or not in PATH.
    echo Please install Apache Maven and add it to your PATH.
    echo Download from: https://maven.apache.org/download.cgi
    echo Or install via chocolatey: choco install maven
    pause
    exit /b 1
)

for /f "tokens=3" %%v in ('mvn -version 2^>^&1 ^| findstr /i "version"') do (
    set "MAVEN_VERSION=%%v"
)
echo [OK] Maven found: %MAVEN_VERSION%

:: ----------------------------------------------------------------------------
:: 3. Setup lib directory and download dependencies
:: ----------------------------------------------------------------------------
if not exist "lib" (
    echo [*] Creating lib directory...
    mkdir lib
)

set "SOLACE_VERSION=10.20.0"
set "SOLACE_JAR=lib\sol-jcsmp-%SOLACE_VERSION%.jar"

if not exist "%SOLACE_JAR%" (
    echo [*] Downloading Solace JCSMP JAR v%SOLACE_VERSION% from Maven Central...
    powershell -Command "& {Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/solacesystems/sol-jcsmp/%SOLACE_VERSION%/sol-jcsmp-%SOLACE_VERSION%.jar' -OutFile '%SOLACE_JAR%'}"
    if exist "%SOLACE_JAR%" (
        echo [OK] Successfully downloaded Solace JCSMP library.
    ) else (
        echo [WARNING] Failed to download Solace JAR automatically.
        echo Please download it manually from Maven Central and place it in the lib folder.
        pause
    )
) else (
    echo [OK] Solace JCSMP library already exists.
)

:: ----------------------------------------------------------------------------
:: 4. Compile if target/classes doesn't exist
:: ----------------------------------------------------------------------------
if not exist "target\classes\com\amhs\swim\test\Main.class" (
    echo [*] Main class not found. Compiling project...
    call mvn clean compile
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Compilation failed. Please check the error messages above.
        pause
        exit /b 1
    )
    echo [OK] Compilation successful.
) else (
    echo [OK] Project already compiled.
)

:: ----------------------------------------------------------------------------
:: 5. Build classpath
:: ----------------------------------------------------------------------------
echo [*] Setting up classpath...

set "CP=target\classes"

:: Add all JARs from lib directory
if exist "lib" (
    for %%f in (lib\*.jar) do (
        set "CP=!CP!;%%f"
    )
)

:: Get Maven dependencies classpath
for /f "delims=" %%i in ('mvn -q dependency:build-classpath -Dmdep.outputFile=CON 2^>nul') do set "MAVEN_CP=%%i"
if defined MAVEN_CP (
    set "CP=%CP%;%MAVEN_CP%"
)

echo [OK] Classpath configured.
echo.

:: ----------------------------------------------------------------------------
:: 6. Run the application
:: ----------------------------------------------------------------------------
echo ============================================================================
echo Starting AMHS/SWIM Gateway Test Tool...
echo ============================================================================
echo.

java -cp "%CP%" com.amhs.swim.test.Main %*

:: Capture exit code
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo ============================================================================
echo Application exited with code: %EXIT_CODE%
echo ============================================================================

:: Keep window open if run directly (not from command line with arguments)
if "%~1"=="" (
    pause
)

exit /b %EXIT_CODE%

:: ============================================================================
:: VERIFIER MODE
:: ============================================================================
:RUN_VERIFIER
shift

echo ============================================================================
echo AMHS/SWIM Verifier - Universal Run Script
echo ============================================================================
echo.

:: Check for Python
where python >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Python is not installed or not in PATH.
    echo Please install Python 3.x and add it to your PATH.
    echo Download from: https://www.python.org/downloads/
    pause
    exit /b 1
)

for /f "tokens=2" %%p in ('python --version 2^>^&1') do (
    set "PYTHON_VERSION=%%p"
)
echo [OK] Python found: %PYTHON_VERSION%

:: Check for qpid-proton library
echo [*] Checking for python-qpid-proton library...
python -c "import proton" >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [*] python-qpid-proton not found. Installing...
    pip install python-qpid-proton
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Failed to install python-qpid-proton.
        echo Please install it manually: pip install python-qpid-proton
        pause
        exit /b 1
    )
    echo [OK] python-qpid-proton installed successfully.
) else (
    echo [OK] python-qpid-proton library found.
)

echo.
echo ============================================================================
echo Starting Verifier...
echo ============================================================================
echo.

:: Pass all remaining arguments to the verifier script
python verifier\verifying_consumer.py %*

:: Capture exit code
set "VERIFIER_EXIT_CODE=%ERRORLEVEL%"

echo.
echo ============================================================================
echo Verifier exited with code: %VERIFIER_EXIT_CODE%
echo ============================================================================

if "%~1"=="" (
    pause
)

exit /b %VERIFIER_EXIT_CODE%
