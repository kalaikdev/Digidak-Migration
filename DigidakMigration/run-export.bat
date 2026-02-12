@echo off
REM ============================================================================
REM Digidak Migration Utility - Export Operation (Legacy Repository)
REM ============================================================================
REM This script runs the Digidak Export operation to extract metadata and 
REM content from the legacy Documentum repository.
REM Uses config-Export folder for legacy repository configuration.
REM ============================================================================

setlocal EnableDelayedExpansion

echo ====================================================================================================
echo  Digidak Migration Utility - EXPORT (Legacy Repository)
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
) else if exist "%ProgramFiles%\Java\jdk-17\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Java\jdk-17"
    echo Using System JDK 17
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

REM Set classpath - using config-Export for legacy repository settings
REM Build classpath with all JARs from Shared folder
set "CLASSPATH=config-Export;target\classes"
for %%i in (..\Shared\*.jar) do set "CLASSPATH=!CLASSPATH!;%%i"

echo.
echo Configuration: config-Export (Legacy Repository)
echo Classpath: !CLASSPATH!
echo Starting Digidak Export...
echo.

if defined JAVA_HOME (
    "%JAVA_HOME%\bin\java" -cp "%CLASSPATH%" com.nabard.digidak.migration.DigidakExportOperation
) else (
    java -cp "%CLASSPATH%" com.nabard.digidak.migration.DigidakExportOperation
)

echo.
echo ====================================================================================================
echo  Export Complete
echo ====================================================================================================
pause
