@echo off
setlocal

set PORT=%1
if "%PORT%"=="" set PORT=8080

set APP_DIR=%~dp0quick-test-app
if not exist "%APP_DIR%\index.html" (
  echo Error: "%APP_DIR%\index.html" not found
  exit /b 1
)

echo Starting Quick Test App at http://localhost:%PORT%
echo Press Ctrl+C to stop.

cd /d "%APP_DIR%"
python -m http.server %PORT%
