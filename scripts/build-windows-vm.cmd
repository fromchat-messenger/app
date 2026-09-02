@echo off
setlocal EnableExtensions

if /I "%1"=="sync" (
  call "%~dp0sync-windows-vm.cmd" %2
  exit /b %ERRORLEVEL%
)

call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" arm64
if errorlevel 1 exit /b 1
call "%~dp0ensure-windows-arm64-jdk.cmd"
if errorlevel 1 exit /b 1
set "FROMCHAT_PACKAGING_JDK=%JAVA_HOME%"
set "PATH=%JAVA_HOME%\bin;C:\WINDOWS\system32\config\systemprofile\.cargo\bin;%PATH%"
cd /d C:\FromChat\app
if exist gradle\gradle-daemon-jvm.properties del /f gradle\gradle-daemon-jvm.properties

if /I "%1"=="rust" (
  call "%~dp0sync-windows-vm.cmd" rust
  if errorlevel 1 exit /b 1
  cd app\desktop\windows-setup
  cargo build --release --workspace
  exit /b %ERRORLEVEL%
)

if /I "%1"=="beta" (
  call "%~dp0sync-windows-vm.cmd" all
  if errorlevel 1 exit /b 1
  if exist gradle\gradle-daemon-jvm.properties del /f gradle\gradle-daemon-jvm.properties
  if /I "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
    if exist app\desktop\build\compose\tmp\main\runtime rmdir /s /q app\desktop\build\compose\tmp\main\runtime
    if exist app\desktop\build\compose\binaries\main\app rmdir /s /q app\desktop\build\compose\binaries\main\app
    if not exist app\desktop\build\prebuilt\windows-x64\app\FromChat\FromChat.exe (
      echo WARNING: No x64 prebuilt at app\desktop\build\prebuilt\windows-x64\app\FromChat
      echo          Installer will be ARM64-only. Run scripts\stage-windows-x64-prebuilt.cmd first.
    )
  )
  gradlew.bat :app:desktop:packageBetaWindows -PbetaDesktop --no-daemon
  if errorlevel 1 exit /b 1
  call "%~dp0copy-installers-to-desktop.cmd" beta
  exit /b %ERRORLEVEL%
)

if /I "%1"=="install" (
  call "%~dp0build-windows-vm.cmd" beta
  if errorlevel 1 exit /b 1
  call "%~dp0run-beta-installer.cmd"
  exit /b %ERRORLEVEL%
)

if /I "%1"=="pack" (
  call "%~dp0sync-windows-vm.cmd" rust
  if errorlevel 1 exit /b 1
  if exist gradle\gradle-daemon-jvm.properties del /f gradle\gradle-daemon-jvm.properties
  gradlew.bat :app:desktop:packSetupOnly --no-daemon
  if errorlevel 1 exit /b 1
  call "%~dp0copy-installers-to-desktop.cmd"
  exit /b %ERRORLEVEL%
)

if /I "%1"=="gradle" (
  call "%~dp0sync-windows-vm.cmd" gradle
  if errorlevel 1 exit /b 1
  if exist gradle\gradle-daemon-jvm.properties del /f gradle\gradle-daemon-jvm.properties
  gradlew.bat :app:desktop:packageReleaseWindows --no-daemon
  if errorlevel 1 exit /b 1
  call "%~dp0copy-installers-to-desktop.cmd"
  exit /b %ERRORLEVEL%
)

echo Usage: build-windows-vm.cmd sync [all^|rust^|scripts^|gradle]
echo        build-windows-vm.cmd rust    ^(sync windows-setup, then cargo build^)
echo        build-windows-vm.cmd beta    ^(debug app + beta setup EXE, no ProGuard^)
echo        build-windows-vm.cmd install ^(beta build + upgrade install + launch^)
echo        Place x64 app-image: scripts\stage-windows-x64-prebuilt.cmd ^<path^>
echo        build-windows-vm.cmd pack    ^(sync, rust, packSetupOnly — fast UI loop^)
echo        build-windows-vm.cmd gradle  ^(sync, full packageReleaseWindows, copy to Desktop^)
exit /b 1
