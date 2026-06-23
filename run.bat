@echo off
setlocal enabledelayedexpansion

:: ============================================================================
:: AMHS/SWIM Gateway Test Tool - Run Script for Windows
:: ============================================================================

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
    echo Please install Java JDK 8+ and add it to your PATH.
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
:: 3. Build/prepare project
:: ----------------------------------------------------------------------------
if not exist "target\classes\com\amhs\swim\test\Main.class" (
    echo [*] Compiling project...
    call mvn clean compile
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Compilation failed!
        pause
        exit /b 1
    )
    echo [OK] Compiled successfully!
)

if not exist "target\dependency" (
    echo [*] Copying dependencies...
    call mvn dependency:copy-dependencies -DoutputDirectory=target\dependency
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Failed to copy dependencies!
        pause
        exit /b 1
    )
    echo [OK] Dependencies copied!
)

:: ----------------------------------------------------------------------------
:: 4. Build classpath
:: ----------------------------------------------------------------------------
echo [*] Building classpath...
set "CLASSPATH=target\classes;lib\*;target\dependency\*"

:: ----------------------------------------------------------------------------
:: 5. Run the application
:: ----------------------------------------------------------------------------
echo ============================================================================
echo Starting AMHS/SWIM Gateway Test Tool...
echo ============================================================================
echo.

java -cp "%CLASSPATH%" com.amhs.swim.test.Main %*

:: Capture exit code
set "EXIT_CODE=%ERRORLEVEL%"

echo.
echo ============================================================================
echo Application exited with code: %EXIT_CODE%
echo ============================================================================

:: Keep window open *always*
pause
exit /b %EXIT_CODE%

:: ============================================================================
:: VERIFIER MODE
:: ============================================================================
:RUN_VERIFIER
shift

echo ============================================================================
echo AMHS/SWIM Verifier
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
    echo [*] python-qpid-proton not found; installing...
    pip install python-qpid-proton
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Failed to install python-qpid-proton.
        echo Please install manually: pip install python-qpid-proton
        pause
        exit /b 1
    )
    echo [OK] python-qpid-proton installed successfully!
) else (
    echo [OK] python-qpid-proton library found!
)

echo.
echo ============================================================================
echo Starting Verifier...
echo ============================================================================
echo.

:: Pass remaining arguments to verifier script
python verifier\verifying_consumer.py %*

set "VERIFIER_EXIT_CODE=%ERRORLEVEL%"

echo.
echo ============================================================================
echo Verifier exited with code: %VERIFIER_EXIT_CODE%
echo ============================================================================

:: Keep window open *always*
pause
exit /b %VERIFIER_EXIT_CODE%
