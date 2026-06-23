@echo off
setlocal enabledelayedexpansion

:: ============================================================================
:: AMHS/SWIM Gateway Test Tool - Verifier Direct Script
:: ============================================================================

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
