@echo off
REM Generate keystore script for AMHS/SWIM Gateway Test Tool - Windows version
REM Compliant with AMHS SEC security standards (EUR Doc 047).

REM Configuration defaults
set KEYSTORE_PATH=..\src\main\resources\security\keystore.p12
set ALIAS=gateway_identity
set KEYALG=RSA
set KEYSIZE=2048
set STORETYPE=PKCS12
set VALIDITY=365
set DNAME=CN=AMHS-SWIM-Gateway, OU=Test, O=TestOrg, C=GB

REM Ensure we're in the scripts directory
cd /d "%~dp0"

REM Check destination directory
if not exist "..\src\main\resources\security" (
    echo Creating security directory...
    mkdir "..\src\main\resources\security"
)

REM Check for keytool
where keytool >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: 'keytool' command not found. Please install Java JDK.
    pause
    exit /b 1
)

REM Information
echo Starting keystore generation at: %KEYSTORE_PATH%
echo Alias: %ALIAS%
echo Algorithm: %KEYALG% (%KEYSIZE%)

REM Generate keystore
REM Note: Script will prompt user to enter keystore password and private key password.
keytool -genkeypair ^
    -alias "%ALIAS%" ^
    -keyalg "%KEYALG%" ^
    -keysize "%KEYSIZE%" ^
    -storetype "%STORETYPE%" ^
    -keystore "%KEYSTORE_PATH%" ^
    -validity "%VALIDITY%" ^
    -dname "%DNAME%"

if %ERRORLEVEL% EQU 0 (
    echo Success: Keystore created at %KEYSTORE_PATH%
    echo Note: Please update the password in config\test.properties
) else (
    echo Error: Failed to create keystore.
    pause
    exit /b 1
)

pause
