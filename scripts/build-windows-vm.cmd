@echo off
setlocal EnableExtensions

if /I "%1"=="sync" (
  call "%~dp0sync-windows-vm.cmd" %2
  exit /b %ERRORLEVEL%
)

call "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvarsall.bat" arm64
if errorlevel 1 exit /b 1
set "JAVA_HOME_17=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
set "JAVA_HOME_21=C:\Program Files\Eclipse Adoptium\jdk-21.0.10.7-hotspot"
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA_HOME_17=%%D"
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do set "JAVA_HOME_21=%%D"
set "JAVA_HOME=%JAVA_HOME_21%"
set "FROMCHAT_PACKAGING_JDK=%JAVA_HOME_17%"
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
echo        build-windows-vm.cmd pack    ^(sync, rust, packSetupOnly — fast UI loop^)
echo        build-windows-vm.cmd gradle  ^(sync, full packageReleaseWindows, copy to Desktop^)
exit /b 1
