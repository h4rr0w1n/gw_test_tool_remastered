@echo off
setlocal

:: ============================================================================
:: AMHS/SWIM Gateway Test Tool - Single Entry Point (Build + Run)
:: Automatically builds from source if Maven is available and JAR is missing.
:: Java auto-detected. No PATH configuration required.
:: ============================================================================

cd /d "%~dp0"

echo ============================================================================
echo  AMHS/SWIM Gateway Test Tool
echo ============================================================================
echo.

:: ─────────────────────────────────────────────────────────────────
:: Locate Java
:: ─────────────────────────────────────────────────────────────────
set "JAVA_EXE="
call :FIND_JAVA

if "%JAVA_EXE%"=="" (
    echo [ERROR] Java Runtime ^(JRE/JDK 8+^) not found on this system.
    echo.
    echo  Please install Java from one of these free sources:
    echo    https://adoptium.net/              ^(Eclipse Temurin - Recommended^)
    echo    https://www.oracle.com/java/        ^(Oracle JDK^)
    echo    https://www.microsoft.com/openjdk   ^(Microsoft OpenJDK^)
    echo.
    echo  After installing, re-run this script. No PATH setup needed.
    pause
    exit /b 1
)
echo [OK] Java: %JAVA_EXE%

:: ─────────────────────────────────────────────────────────────────
:: Check if JAR exists; if not, attempt to build from source
:: ─────────────────────────────────────────────────────────────────
if not exist "amhs-swim-tool.jar" (
    echo [INFO] amhs-swim-tool.jar not found. Attempting to build from source...
    call :TRY_BUILD
    if errorlevel 1 (
        echo [ERROR] Build failed and no pre-built JAR is available.
        echo         Please ensure Maven ^(mvn^) and JDK 8+ are installed and in PATH.
        pause
        exit /b 1
    )
)

if not exist "amhs-swim-tool.jar" (
    echo [ERROR] amhs-swim-tool.jar still not found after build attempt.
    pause
    exit /b 1
)

echo [OK] JAR:  amhs-swim-tool.jar
echo.
echo ============================================================================
echo  Starting AMHS/SWIM Gateway Test Tool...
echo ============================================================================
echo.

"%JAVA_EXE%" -jar "amhs-swim-tool.jar" %*

set "EXIT_CODE=%ERRORLEVEL%"
echo.
echo ============================================================================
echo  Application exited with code: %EXIT_CODE%
echo ============================================================================
pause
exit /b %EXIT_CODE%


:: ============================================================================
:: :TRY_BUILD  - Tries to build with Maven; copies JAR to root on success.
:: ============================================================================
:TRY_BUILD
where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo [WARN] Maven ^(mvn^) not found in PATH. Cannot build from source.
    echo        Download Maven from: https://maven.apache.org/download.cgi
    exit /b 1
)
if not exist "pom.xml" (
    echo [WARN] pom.xml not found. Cannot build.
    exit /b 1
)
echo [INFO] Building from source using Maven ^(this may take a minute^)...
echo.
call mvn clean package -DskipTests -q
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Maven build failed. Check the output above for details.
    exit /b 1
)
if exist "target\test-tool-1.0.0-jar-with-dependencies.jar" (
    copy /Y "target\test-tool-1.0.0-jar-with-dependencies.jar" "amhs-swim-tool.jar" >nul
    echo [OK] Build successful. amhs-swim-tool.jar updated.
) else (
    echo [ERROR] Build completed but expected JAR not found in target\.
    exit /b 1
)
goto :EOF


:: ============================================================================
:: :FIND_JAVA  - Sets JAVA_EXE, returns via goto :EOF
:: Uses separate for /d lines to avoid nested-loop + goto crash.
:: ============================================================================
:FIND_JAVA
:: 1. Already in PATH?
where java >nul 2>nul
if %ERRORLEVEL% EQU 0 ( set "JAVA_EXE=java" & goto :EOF )

:: 2. JAVA_HOME set?
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
        goto :EOF
    )
)

:: 3. Common install locations (one for /d per vendor - avoids nested goto crash)
for /d %%J in ("C:\Program Files\Java\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
)
for /d %%J in ("C:\Program Files\Eclipse Adoptium\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
    for /d %%K in ("%%J\*") do (
        if exist "%%K\bin\java.exe" ( set "JAVA_EXE=%%K\bin\java.exe" & goto :EOF )
    )
)
for /d %%J in ("C:\Program Files\Microsoft\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
    for /d %%K in ("%%J\*") do (
        if exist "%%K\bin\java.exe" ( set "JAVA_EXE=%%K\bin\java.exe" & goto :EOF )
    )
)
for /d %%J in ("C:\Program Files\Amazon Corretto\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
)
for /d %%J in ("C:\Program Files\BellSoft\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
)
for /d %%J in ("C:\Program Files\Zulu\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
)
for /d %%J in ("C:\Program Files\OpenJDK\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
)
for /d %%J in ("C:\Program Files (x86)\Java\*") do (
    if exist "%%J\bin\java.exe" ( set "JAVA_EXE=%%J\bin\java.exe" & goto :EOF )
)
goto :EOF
