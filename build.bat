@echo off
:: ============================================================================
:: Developer Build Script - AMHS/SWIM Gateway Test Tool
:: Requires: Apache Maven 3.6+ and Java JDK 8+
:: Output:   amhs-swim-tool.jar  (fat JAR, all dependencies bundled)
:: ============================================================================
setlocal
cd /d "%~dp0"

echo [INFO] Building AMHS/SWIM Gateway Test Tool...
echo [INFO] This requires Maven and JDK to be installed.
echo.

:: Check for Maven
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven (mvn) not found in PATH.
    echo         Download from: https://maven.apache.org/download.cgi
    pause
    exit /b 1
)

call mvn clean package -DskipTests
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed! Check output above for details.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [INFO] Copying fat JAR to root directory...
copy /Y "target\test-tool-1.0.0-jar-with-dependencies.jar" "amhs-swim-tool.jar"

echo.
echo [SUCCESS] Build complete!
echo.
echo   amhs-swim-tool.jar  - Ready to distribute
echo.
echo To create a release ZIP, run: package-release.bat
pause
