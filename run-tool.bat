@echo off
setlocal

:: ============================================================================
:: AMHS/SWIM Gateway Test Tool - Main Tool Runner (Plug & Play)
:: No Maven or PATH configuration required. Java auto-detected.
:: ============================================================================

cd /d "%~dp0"

echo ============================================================================
echo  AMHS/SWIM Gateway Test Tool
echo ============================================================================
echo.

:: Locate Java via subroutine (avoids goto-inside-for crash)
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

if not exist "amhs-swim-tool.jar" (
    echo [ERROR] amhs-swim-tool.jar not found in this directory.
    echo         If you are a developer, run build.bat first.
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
