@echo off
call "%~dp0gradlew.bat" build
if errorlevel 1 exit /b %ERRORLEVEL%
echo.
echo Build complete. Look in build\libs\ for the mod JAR.
