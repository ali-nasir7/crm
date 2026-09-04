@echo off
REM Maven Wrapper (bootstrap edition for Windows): requires Maven on PATH or PowerShell to download.
where mvn >nul 2>nul
if %errorlevel%==0 ( mvn %* ) else (
  echo Please install Apache Maven 3.9+ or run the backend from Docker Compose.
  exit /b 1
)
