@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Ensure a native Windows ARM64 JDK is available for Gradle/jpackage builds.
rem x64 JDKs under emulation package x64 Skiko/JavaFX natives and break the desktop app.

if /I not "%PROCESSOR_ARCHITECTURE%"=="ARM64" (
  if /I not "%PROCESSOR_ARCHITEW6432%"=="ARM64" (
    exit /b 0
  )
)

set "JDK_ROOT=C:\Program Files\Eclipse Adoptium"
set "JDK_HOME="
set "JDK_FALLBACK=%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21-arm64"

for /d %%D in ("%JDK_ROOT%\jdk-*-arm64") do (
  if exist "%%D\bin\java.exe" set "JDK_HOME=%%D"
)
for /d %%D in ("%JDK_ROOT%\jdk-*aarch64*") do (
  if exist "%%D\bin\java.exe" set "JDK_HOME=%%D"
)

if not defined JDK_HOME (
  for /d %%D in ("%JDK_ROOT%\jdk-*") do (
    if not defined JDK_HOME if exist "%%D\bin\java.exe" (
      "%%D\bin\java.exe" -XshowSettings:properties -version 2>&1 | findstr /i /c:"os.arch = aarch64" >nul
      if not errorlevel 1 set "JDK_HOME=%%D"
    )
  )
)

if defined JDK_HOME goto :verify

echo Installing Temurin JDK 21 for Windows ARM64...
set "TMP_ZIP=%TEMP%\fromchat-temurin21-arm64.zip"
set "TMP_DIR=%TEMP%\fromchat-temurin21-arm64"
set "JDK_HOME=%JDK_ROOT%\jdk-21.0.12.101-hotspot-arm64"

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$zip='%TMP_ZIP%'; $tmp='%TMP_DIR%'; $dest='%JDK_HOME%'; $fallback='%JDK_FALLBACK%';" ^
  "try { New-Item -ItemType Directory -Force -Path $dest | Out-Null } catch { $dest = $fallback; New-Item -ItemType Directory -Force -Path $dest | Out-Null };" ^
  "Invoke-WebRequest -Uri 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/aarch64/jdk/hotspot/normal/eclipse?project=jdk' -OutFile $zip;" ^
  "if (Test-Path $tmp) { Remove-Item -Recurse -Force $tmp };" ^
  "Expand-Archive -Path $zip -DestinationPath $tmp -Force;" ^
  "$inner = Get-ChildItem $tmp -Directory | Select-Object -First 1;" ^
  "Copy-Item -Path (Join-Path $inner.FullName '*') -Destination $dest -Recurse -Force"

if not exist "%JDK_HOME%\bin\java.exe" set "JDK_HOME=%JDK_FALLBACK%"

if not exist "%JDK_HOME%\bin\java.exe" (
  echo ERROR: Failed to install ARM64 JDK under %JDK_ROOT%
  exit /b 1
)

:verify
"%JDK_HOME%\bin\java.exe" -XshowSettings:properties -version 2>&1 | findstr /i /c:"os.arch = aarch64" >nul
if errorlevel 1 (
  echo ERROR: %JDK_HOME% is not an ARM64 JDK ^(os.arch must be aarch64^).
  exit /b 1
)

set "JAVA_HOME=%JDK_HOME%"
set "FROMCHAT_PACKAGING_JDK=%JDK_HOME%"
set "PATH=%JDK_HOME%\bin;%PATH%"
echo Using ARM64 JDK: %JDK_HOME%
endlocal & set "JAVA_HOME=%JAVA_HOME%" & set "FROMCHAT_PACKAGING_JDK=%FROMCHAT_PACKAGING_JDK%" & set "PATH=%PATH%"
exit /b 0
