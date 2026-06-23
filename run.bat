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

:: Keep window open always
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

:: Check for Java
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

:: Check if classes and dependencies exist
if not exist "target\classes\com\amhs\swim\test\verifier\Verifier.class" (
    echo [*] Compiling project...
    call mvn clean compile dependency:copy-dependencies -DoutputDirectory=target/dependency
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Compilation failed!
        pause
        exit /b 1
    )
)

if not exist "target\dependency" (
    echo [*] Copying dependencies...
    call mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
    if %ERRORLEVEL% NEQ 0 (
        echo [ERROR] Failed to copy dependencies!
        pause
        exit /b 1
    )
)

echo ============================================================================
echo Starting Verifier...
echo ============================================================================
echo.

java -cp "target\classes;lib\*;target\dependency\*" com.amhs.swim.test.verifier.Verifier %*

set "VERIFIER_EXIT_CODE=%ERRORLEVEL%"

echo.
echo ============================================================================
echo Verifier exited with code: %VERIFIER_EXIT_CODE%
echo ============================================================================

:: Keep window open always
pause
exit /b %VERIFIER_EXIT_CODE%
