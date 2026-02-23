@echo off
REM ============================================================================
REM Digidak Migration Utility - Compile and Export Operation
REM ============================================================================
REM This script compiles the Java source files and runs the Digidak Export.
REM Uses config-Export folder for legacy repository configuration.
REM ============================================================================

setlocal EnableDelayedExpansion

echo ====================================================================================================
echo  Digidak Migration Utility - COMPILE AND EXPORT
echo ====================================================================================================
echo.

REM -----------------------------------------------------------------------
REM Step 1: Locate Java
REM -----------------------------------------------------------------------
set "JAVA_BIN="
if exist "java\bin\java.exe" (
    set "JAVA_BIN=java\bin"
    echo Using bundled Java from java\ folder
) else if exist "..\dist\java\bin\java.exe" (
    set "JAVA_BIN=..\dist\java\bin"
    echo Using bundled Java from ..\dist\java\ folder
) else if exist "%ProgramFiles%\Java\jdk-17\bin\java.exe" (
    set "JAVA_BIN=%ProgramFiles%\Java\jdk-17\bin"
    echo Using System JDK 17
) else (
    where java >nul 2>&1
    if errorlevel 1 (
        echo ERROR: Java not found. Please install Java or place bundled Java in 'java' folder.
        pause
        exit /b 1
    )
    echo Using system Java
)

REM -----------------------------------------------------------------------
REM Step 2: Build classpath from Shared JARs
REM -----------------------------------------------------------------------
set "COMPILE_CP=config-Export;target\classes"
for %%i in (..\Shared\*.jar) do set "COMPILE_CP=!COMPILE_CP!;%%i"

REM -----------------------------------------------------------------------
REM Step 3: Ensure target\classes directory exists
REM -----------------------------------------------------------------------
if not exist "target\classes" (
    mkdir "target\classes"
    echo Created target\classes directory
)

REM -----------------------------------------------------------------------
REM Step 4: Compile all Java source files
REM -----------------------------------------------------------------------
echo.
echo Compiling Java source files...
echo.

if defined JAVA_BIN (
    "%JAVA_BIN%\javac" -cp "%COMPILE_CP%" -d "target\classes" -encoding UTF-8 src\main\java\com\nabard\digidak\migration\*.java
) else (
    javac -cp "%COMPILE_CP%" -d "target\classes" -encoding UTF-8 src\main\java\com\nabard\digidak\migration\*.java
)

if errorlevel 1 (
    echo.
    echo ERROR: Compilation failed. Please check the errors above.
    pause
    exit /b 1
)

echo.
echo Compilation successful.
echo.

REM -----------------------------------------------------------------------
REM Step 5: Run Export
REM -----------------------------------------------------------------------
echo ====================================================================================================
echo  Starting Digidak Export...
echo ====================================================================================================
echo.
echo Configuration : config-Export (Legacy Repository)
echo.

if defined JAVA_BIN (
    "%JAVA_BIN%\java" -cp "%COMPILE_CP%" com.nabard.digidak.migration.DigidakExportOperation
) else (
    java -cp "%COMPILE_CP%" com.nabard.digidak.migration.DigidakExportOperation
)

echo.
echo ====================================================================================================
echo  Export Complete
echo ====================================================================================================
pause
