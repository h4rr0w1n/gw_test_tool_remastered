@echo off
REM AMQP Universal Payload Verification Runner for Windows
REM This script runs the standalone AMQP consumer to verify any payload delivery.

REM 1. Check for Python
where python >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    where python3 >nul 2>nul
    if %ERRORLEVEL% NEQ 0 (
        echo Error: Python is not installed. Please install Python 3.
        pause
        exit /b 1
    ) else (
        set PYTHON_CMD=python3
    )
) else (
    set PYTHON_CMD=python
)

REM 2. Check for python-proton library
%PYTHON_CMD% -c "import proton" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [!] 'proton' library not found.
    echo [*] Attempting to install python-qpid-proton via pip...
    
    REM Try pip3 first, then pip
    %PYTHON_CMD% -m pip3 install python-qpid-proton >nul 2>&1
    if %ERRORLEVEL% NEQ 0 (
        %PYTHON_CMD% -m pip install python-qpid-proton
        if %ERRORLEVEL% NEQ 0 (
            echo [-] Error: Failed to install proton library automatically.
            echo Please install it manually: %PYTHON_CMD% -m pip install python-qpid-proton
            pause
            exit /b 1
        )
    )
    echo [+] proton library installed successfully.
)

REM 3. Run the verifier
REM It will automatically pick up settings from config\test.properties
echo [*] Starting Universal AMQP Verifier...

REM Get the script directory and change to it
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

%PYTHON_CMD% verifier\verifying_consumer.py %*

REM Keep window open if run directly (not from command line)
if "%~1"=="" pause
