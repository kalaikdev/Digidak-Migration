@echo off
REM ============================================================================
REM Digidak Migration Utility - Import Operation
REM ============================================================================
REM This script runs the Digidak Import operation to load metadata and 
REM content into the target Documentum repository.
REM ============================================================================

setlocal EnableDelayedExpansion

echo ====================================================================================================
echo  Digidak Migration Utility - IMPORT
echo ====================================================================================================
echo.

REM Check if Java is available
set JAVA_HOME=
if exist "java\bin\java.exe" (
    set "JAVA_HOME=java"
    echo Using bundled Java from java\ folder
) else if exist "..\dist\java\bin\java.exe" (
    set "JAVA_HOME=..\dist\java"
    echo Using bundled Java from ..\dist\java\ folder
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        echo ERROR: Java not found. Please install Java 17 or place bundled Java in 'java' folder.
        pause
        exit /b 1
    )
    echo Using system Java
    set "JAVA_HOME="
)

REM Set classpath
set "CLASSPATH=config;lib\*;..\Shared\*"

echo.
echo Starting Digidak Import...
echo.

if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java" -cp "%CLASSPATH%" com.nabard.digidak.migration.DigidakImportOperation
) else (
    java -cp "%CLASSPATH%" com.nabard.digidak.migration.DigidakImportOperation
)

echo.
echo ====================================================================================================
echo  Import Complete
echo ====================================================================================================
pause
