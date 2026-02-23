@echo off
REM ============================================
REM DigiDak Migration - Compile Only
REM ============================================
echo.
echo ============================================
echo DigiDak Migration - Compile Only
echo ============================================
echo.

REM Set working directory
cd /d "%~dp0"

REM Compile source files
echo [1/2] Compiling source files...
dir /s /b src\main\java\com\digidak\migration\util\*.java src\main\java\com\digidak\migration\model\*.java src\main\java\com\digidak\migration\config\*.java src\main\java\com\digidak\migration\repository\*.java src\main\java\com\digidak\migration\parser\*.java src\main\java\com\digidak\migration\service\*.java > _sources.txt
javac -encoding UTF-8 -cp "libs/*;." -d . @_sources.txt

if %ERRORLEVEL% NEQ 0 (
    del _sources.txt 2>nul
    echo ERROR: Source compilation failed!
    pause
    exit /b 1
)
del _sources.txt 2>nul
echo [OK] Source files compiled
echo.

REM Compile phase runners
echo [2/2] Compiling phase runners...
javac -cp "libs/*;." Phase1Runner.java Phase2Runner.java Phase3Runner.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase runner compilation failed!
    pause
    exit /b 1
)
echo [OK] Phase runners compiled
echo.

echo ============================================
echo COMPILATION COMPLETED SUCCESSFULLY!
echo ============================================
echo.
echo You can now run:
echo   - run_migration_quick.bat (to execute all phases)
echo   - Or individual phase runners
echo.
pause
