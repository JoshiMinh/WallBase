@echo off
setlocal enabledelayedexpansion

:: Load variables from .env
if exist .env (
    for /f "usebackq tokens=1,2 delims==" %%i in (".env") do (
        set "%%i=%%j"
    )
) else (
    echo [ERROR] .env file NOT found.
    echo Creating a default .env file...
    echo DEVICE_ID=emulator-5554 > .env
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

:MENU
cls
echo ===========================================
echo         WallBase Build/Run Center
echo ===========================================
echo  Target Device:  %DEVICE_ID%
echo  Package:        %PACKAGE_NAME%
echo ===========================================
echo  [1] Build (assembleDebug)
echo  [2] Install (installDebug)
echo  [3] Run (adb shell am start)
echo  [4] Quick All (Install + Run)
echo  [5] Full All (Clean + Install + Run)
echo  [6] Clean Only
echo  [Q] Exit
echo ===========================================
set /p opt="Your selection: "

if "%opt%"=="1" goto BUILD
if "%opt%"=="2" goto INSTALL
if "%opt%"=="3" goto RUN
if "%opt%"=="4" goto QUICK
if "%opt%"=="5" goto FULL
if "%opt%"=="6" goto CLEAN
if /i "%opt%"=="Q" goto EXIT
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

:RUN
echo.
echo [STATUS] Launching %PACKAGE_NAME%/%MAIN_ACTIVITY% on %DEVICE_ID%...
"%ADB_PATH%" -s %DEVICE_ID% shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
pause
goto MENU

:QUICK
echo.
echo [STATUS] Running Quick Install and Launch...
call gradlew.bat installDebug
"%ADB_PATH%" -s %DEVICE_ID% shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
pause
goto MENU

:FULL
echo.
echo [STATUS] Running Full Clean, Build, Install, and Launch...
call gradlew.bat clean
call gradlew.bat installDebug
"%ADB_PATH%" -s %DEVICE_ID% shell am start -n %PACKAGE_NAME%/%MAIN_ACTIVITY%
pause
goto MENU

:CLEAN
echo.
echo [STATUS] Cleaning build artifacts...
call gradlew.bat clean
pause
goto MENU

:EXIT
echo Exiting...
exit /b
