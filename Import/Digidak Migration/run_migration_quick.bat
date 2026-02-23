@echo off
REM ============================================
REM DigiDak Migration - Quick Run (No Compile)
REM ============================================
echo.
echo ============================================
echo DigiDak Migration - Quick Run
echo ============================================
echo.
echo NOTE: This assumes all files are already compiled
echo       Use run_migration.bat for full compile + run
echo.

REM Set working directory
cd /d "%~dp0"

REM Execute Phase 1
echo [1/3] Executing Phase 1 - Creating Folder Structure...
echo.
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -Dlog4j2.configurationFile=config/log4j2.properties -cp "libs/*;config;." Phase1Runner
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase 1 failed!
    pause
    exit /b 1
)
echo.

REM Execute Phase 2
echo [2/3] Executing Phase 2 - Importing Documents...
echo.
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -Dlog4j2.configurationFile=config/log4j2.properties -cp "libs/*;config;." Phase2Runner
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase 2 failed!
    pause
    exit /b 1
)
echo.

REM Execute Phase 3
echo [3/3] Executing Phase 3 - Creating Movement Registers...
echo.
java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -Dlog4j2.configurationFile=config/log4j2.properties -cp "libs/*;config;." Phase3Runner
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase 3 failed!
    pause
    exit /b 1
)

echo.
echo ============================================
echo MIGRATION COMPLETED SUCCESSFULLY!
echo ============================================
echo.
pause
