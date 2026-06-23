@echo off
REM Package a release of AMHS/SWIM Gateway Test Tool
setlocal
cd /d "%~dp0"

set RELEASE_DIR=release
set RELEASE_NAME=amhs-swim-gateway-test-tool

echo [INFO] Creating release package...

REM Clean previous release
if exist "%RELEASE_DIR%" (
    rmdir /s /q "%RELEASE_DIR%"
)
if exist "%RELEASE_NAME%.zip" (
    del /f /q "%RELEASE_NAME%.zip"
)

REM Create release directory
mkdir "%RELEASE_DIR%"

REM Copy main files
copy /Y "amhs-swim-tool.jar" "%RELEASE_DIR%\"
copy /Y "config\test.properties" "%RELEASE_DIR%\config\"
copy /Y "config\default_case_payloads.xml" "%RELEASE_DIR%\config\"
copy /Y "cases.json" "%RELEASE_DIR%\"
copy /Y "run-tool.bat" "%RELEASE_DIR%\"
copy /Y "run-verifier.bat" "%RELEASE_DIR%\"

REM Copy materials folder
xcopy /E /I /Y "materials" "%RELEASE_DIR%\materials"

REM Create zip file
powershell -Command "Compress-Archive -Path '%RELEASE_DIR%\*' -DestinationPath '%RELEASE_NAME%.zip' -Force"

REM Clean up release directory
rmdir /s /q "%RELEASE_DIR%"

echo.
echo [SUCCESS] Release package created: %RELEASE_NAME%.zip
pause
