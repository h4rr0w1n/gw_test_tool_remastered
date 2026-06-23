@echo off
setlocal enabledelayedexpansion

:: ============================================================================
:: AMHS/SWIM Verifier
:: ============================================================================

cd /d "%~dp0" || exit /b 1

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

echo.
echo ============================================================================
echo Verifier exited with code: %ERRORLEVEL%
echo ============================================================================

:: Keep window open always
pause
exit /b %ERRORLEVEL%
