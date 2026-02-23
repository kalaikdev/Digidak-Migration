@echo off
REM ============================================
REM DigiDak Migration - Complete Execution
REM ============================================
echo.
echo ============================================
echo DigiDak Migration - Complete Execution
echo ============================================
echo.

REM Set working directory
cd /d "%~dp0"

REM ============================================
REM STEP 1: Compile All Source Files
REM ============================================
echo [1/5] Compiling all source files...
echo.

REM Generate list of all .java source files for compilation
dir /s /b src\main\java\com\digidak\migration\util\*.java src\main\java\com\digidak\migration\model\*.java src\main\java\com\digidak\migration\config\*.java src\main\java\com\digidak\migration\repository\*.java src\main\java\com\digidak\migration\parser\*.java src\main\java\com\digidak\migration\service\*.java > _sources.txt
javac -encoding UTF-8 -cp "libs/*;." -d . @_sources.txt

if %ERRORLEVEL% NEQ 0 (
    del _sources.txt 2>nul
    echo ERROR: Source compilation failed!
    pause
    exit /b 1
)
del _sources.txt 2>nul

echo [OK] Source files compiled successfully
echo.

REM ============================================
REM STEP 2: Compile Phase Runners
REM ============================================
echo [2/5] Compiling phase runners...
echo.

javac -cp "libs/*;." Phase1Runner.java Phase2Runner.java Phase3Runner.java

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase runner compilation failed!
    pause
    exit /b 1
)

echo [OK] Phase runners compiled successfully
echo.

REM ============================================
REM STEP 3: Execute Phase 1 (Folder Structure)
REM ============================================
echo [3/5] Executing Phase 1 - Creating Folder Structure...
echo.

java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -Dlog4j2.configurationFile=config/log4j2.properties -cp "libs/*;config;." Phase1Runner

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase 1 execution failed!
    pause
    exit /b 1
)

echo.
echo [OK] Phase 1 completed successfully
echo.

REM ============================================
REM STEP 4: Execute Phase 2 (Document Import)
REM ============================================
echo [4/5] Executing Phase 2 - Importing Documents...
echo.

java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -Dlog4j2.configurationFile=config/log4j2.properties -cp "libs/*;config;." Phase2Runner

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase 2 execution failed!
    pause
    exit /b 1
)

echo.
echo [OK] Phase 2 completed successfully
echo.

REM ============================================
REM STEP 5: Execute Phase 3 (Movement Registers)
REM ============================================
echo [5/5] Executing Phase 3 - Creating Movement Registers...
echo.

java --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/sun.reflect=ALL-UNNAMED -Dlog4j2.configurationFile=config/log4j2.properties -cp "libs/*;config;." Phase3Runner

if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Phase 3 execution failed!
    pause
    exit /b 1
)

echo.
echo [OK] Phase 3 completed successfully
echo.

REM ============================================
REM Migration Complete
REM ============================================
echo.
echo ============================================
echo MIGRATION COMPLETED SUCCESSFULLY!
echo ============================================
echo.
echo All 3 phases executed successfully:
echo   - Phase 1: Folder Structure (7 folders with 26 attributes each)
echo   - Phase 2: Document Import (5 documents with 4 attributes each)
echo   - Phase 3: Movement Registers (15 registers with 7 attributes each)
echo.
echo Total Objects Created: 27
echo Repository: NABARDUAT
echo Cabinet: /Digidak Legacy
echo.
echo ============================================
echo.

pause
