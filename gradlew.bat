@echo off
setlocal
set GRADLE_VERSION=8.8
set BASE_DIR=%USERPROFILE%\.gradle\worldatwar-bootstrap
set DIST_DIR=%BASE_DIR%\gradle-%GRADLE_VERSION%
set ZIP_FILE=%BASE_DIR%\gradle-%GRADLE_VERSION%-bin.zip
if not exist "%DIST_DIR%\bin\gradle.bat" (
  if not exist "%BASE_DIR%" mkdir "%BASE_DIR%"
  if not exist "%ZIP_FILE%" (
    echo Downloading Gradle %GRADLE_VERSION% for first-time setup...
    powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip' -OutFile '%ZIP_FILE%'"
    if errorlevel 1 exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%ZIP_FILE%' -DestinationPath '%BASE_DIR%' -Force"
  if errorlevel 1 exit /b 1
)
call "%DIST_DIR%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%
