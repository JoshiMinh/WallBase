@echo off
setlocal enabledelayedexpansion

:: ===========================================
:: WallBase - Run WITHOUT Android Studio
:: ===========================================

:: Load variables from .env
if exist .env (
    for /f "usebackq tokens=1,2 delims==" %%i in (".env") do (
        set "%%i=%%j"
    )
) else (
    echo [ERROR] .env file NOT found.
    echo Creating a default .env file...
    echo DEVICE_ID=emulator-5554 > .env
    echo EMULATOR_NAME=Pixel_9 >> .env
    echo ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk >> .env
    echo JAVA_HOME=C:\Program Files\Java\jdk-17 >> .env
    echo ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe >> .env
    echo PACKAGE_NAME=com.joshiminh.wallbase >> .env
    echo MAIN_ACTIVITY=.MainActivity >> .env
    echo.
    echo Please review and edit the generated .env file.
    pause
    exit /b
)

:: Set defaults if missing
if "%EMULATOR_PATH%"=="" set "EMULATOR_PATH=%ANDROID_HOME%\emulator\emulator.exe"
if "%ADB_PATH%"=="" set "ADB_PATH=%ANDROID_HOME%\platform-tools\adb.exe"

:MENU
cls
echo ===========================================
echo       WallBase (Run without AS)
echo ===========================================
echo  Target Device:  %DEVICE_ID%
echo  Package:        %PACKAGE_NAME%
echo ===========================================
echo  [1] Full All (Clean + Build + Install + Run)
echo  [2] Build (assembleDebug)
echo  [3] Install (installDebug)
echo  [4] Start Emulator (%EMULATOR_NAME%)
echo  [5] Run (adb shell am start)
echo  [6] Others Commands
echo  [Q] Exit
echo ===========================================
set /p opt="Your selection: "

if "%opt%"=="1" goto FULL
if "%opt%"=="2" goto BUILD
if "%opt%"=="3" goto INSTALL
if "%opt%"=="4" goto START_EMULATOR
if "%opt%"=="5" goto RUN
if "%opt%"=="6" goto OTHERS
if /i "%opt%"=="Q" goto EXIT
goto MENU

:FULL
echo.
echo [STATUS] Full All: Clean, Build, Install, Launch...
call gradlew.bat clean
call gradlew.bat installDebug
"%ADB_PATH%" -s %DEVICE_ID% shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
pause
goto MENU

:BUILD
echo.
echo [STATUS] Building Debug APK...
call gradlew.bat assembleDebug
pause
goto MENU

:INSTALL
echo.
echo [STATUS] Installing on %DEVICE_ID%...
call gradlew.bat installDebug
pause
goto MENU

:START_EMULATOR
echo.
echo [STATUS] Starting Emulator (%EMULATOR_NAME%)...
if "%EMULATOR_NAME%"=="" (
    echo [ERROR] EMULATOR_NAME not set in .env
) else (
    start "" "%EMULATOR_PATH%" -avd %EMULATOR_NAME%
    echo Emulator launch command sent.
)
pause
goto MENU

:RUN
echo.
echo [STATUS] Launching %PACKAGE_NAME%/%MAIN_ACTIVITY% on %DEVICE_ID%...
"%ADB_PATH%" -s %DEVICE_ID% shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
pause
goto MENU

:OTHERS
cls
echo ===========================================
echo            Others Commands
echo ===========================================
echo  [1] Clean Only
echo  [2] Logcat (Current App)
echo  [3] List AVDs
echo  [4] List Devices
echo  [B] Back
echo ===========================================
set /p subopt="Your selection: "

if "%subopt%"=="1" (
    call gradlew.bat clean
    pause
    goto OTHERS
)
if "%subopt%"=="2" (
    "%ADB_PATH%" -s %DEVICE_ID% logcat %PACKAGE_NAME%:V *:S
    pause
    goto OTHERS
)
if "%subopt%"=="3" (
    "%EMULATOR_PATH%" -list-avds
    pause
    goto OTHERS
)
if "%subopt%"=="4" (
    "%ADB_PATH%" devices
    pause
    goto OTHERS
)
if /i "%subopt%"=="B" goto MENU
goto OTHERS

:EXIT
echo Exiting...
exit /b
