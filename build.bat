@echo off
REM Build executable JAR with all dependencies
setlocal
cd /d "%~dp0"

echo [INFO] Building AMHS/SWIM Gateway Test Tool...
call mvn clean package
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed!
    pause
    exit /b %ERRORLEVEL%
)

echo [INFO] Copying executable JAR to root directory...
copy /Y "target\test-tool-1.0.0-jar-with-dependencies.jar" "amhs-swim-tool.jar"

echo.
echo [SUCCESS] Build complete!
echo Executable JAR is at: amhs-swim-tool.jar
echo.
echo You can now run:
echo   - run-tool.bat (for the main test tool GUI)
echo   - run-verifier.bat (for the message verifier)
pause
