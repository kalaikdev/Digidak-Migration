@echo off
REM ============================================================================
REM Digidak Migration Utility - Package Builder
REM ============================================================================
REM This script builds the project and creates a distribution package.
REM ============================================================================

setlocal EnableDelayedExpansion

echo ====================================================================================================
echo  Digidak Migration Utility - Package Builder
echo ====================================================================================================
echo.

REM Check for Maven
where mvn >nul 2>&1
if errorlevel 1 (
    echo ERROR: Maven not found. Please install Maven and add it to PATH.
    pause
    exit /b 1
)

echo Step 1: Cleaning and compiling project...
call mvn clean compile -q
if errorlevel 1 (
    echo ERROR: Maven build failed.
    pause
    exit /b 1
)

echo Step 2: Creating distribution folder...
if exist "dist" rmdir /s /q dist
mkdir dist
mkdir dist\config
mkdir dist\lib
mkdir dist\logs

echo Step 3: Copying compiled classes...
xcopy /s /q target\classes\* dist\lib\ >nul 2>&1

echo Step 4: Copying configuration files...
copy config\* dist\config\ >nul 2>&1

echo Step 5: Copying dependencies...
xcopy /s /q ..\Shared\*.jar dist\lib\ >nul 2>&1

echo Step 6: Copying batch files...
copy run-export.bat dist\ >nul 2>&1
copy run-import.bat dist\ >nul 2>&1

echo Step 7: Copying Java runtime (if exists)...
if exist "..\dist\java" (
    xcopy /s /q /i ..\dist\java dist\java >nul 2>&1
    echo   Java runtime copied.
) else (
    echo   Note: No bundled Java found. Users will need Java 17 in PATH.
)

echo.
echo ====================================================================================================
echo  Package created successfully in 'dist' folder
echo ====================================================================================================
echo.
echo Contents:
echo   dist\config\   - Configuration files
echo   dist\lib\      - Compiled classes and dependencies
echo   dist\logs\     - Runtime logs (empty)
echo   dist\java\     - Bundled JDK (if available)
echo   dist\run-export.bat  - Export script
echo   dist\run-import.bat  - Import script
echo.
pause
