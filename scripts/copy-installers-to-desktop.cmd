@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Copy packaged Windows installer to the logged-in user's Desktop.
rem prlctl exec runs as SYSTEM, so do not rely on %%USERPROFILE%%.

set "DIST=C:\FromChat\app\app\desktop\build\distributions\release"
set "WIN_DESKTOP="
set "COPIED=0"

if not exist "%DIST%\" (
  echo WARNING: Release output folder missing: %DIST%
  exit /b 0
)

for /f "skip=1 tokens=1" %%U in ('query user 2^>nul') do (
  if not defined LOGON set "LOGON=%%U"
)
if defined LOGON set "WIN_DESKTOP=C:\Users\%LOGON%\Desktop"

if not exist "%WIN_DESKTOP%\" (
  for /d %%D in ("C:\Users\*") do (
    if not defined WIN_DESKTOP (
      echo %%~nxD | findstr /i /x "Public Default DefaultAccount WsiAccount" >nul
      if errorlevel 1 if exist "%%D\Desktop\" set "WIN_DESKTOP=%%D\Desktop"
    )
  )
)

if not defined WIN_DESKTOP (
  echo ERROR: Could not resolve Windows user Desktop path
  exit /b 1
)

call :kill_running_installers

for %%F in ("%DIST%\FromChat-Portable-*.exe") do (
  if exist "%%F" (
    del /F /Q "%%F" >nul 2>&1
    echo Removed stale portable build output: %%~nxF
  )
)

for %%F in ("%WIN_DESKTOP%\FromChat-Portable-*.exe") do (
  if exist "%%F" (
    del /F /Q "%%F" >nul 2>&1
    echo Removed stale portable artifact: %%~nxF
  )
)

for %%F in ("%DIST%\FromChat-Setup-*.exe") do (
  if exist "%%F" (
    call :copy_installer "%%~fF" "%WIN_DESKTOP%"
    if errorlevel 1 exit /b 1
    set "COPIED=1"
  )
)

if "!COPIED!"=="0" (
  echo WARNING: No FromChat-Setup-*.exe found in %DIST%
)

exit /b 0

:kill_running_installers
echo Stopping running FromChat installer processes...
powershell -NoProfile -Command ^
  "$names = @('fromchat-setup','FromChat-Setup*','FromChat-Portable*');" ^
  "foreach ($n in $names) { Get-Process -Name $n -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue }" ^
  2>nul
ping -n 2 127.0.0.1 >nul
exit /b 0

:copy_installer
set "SRC=%~1"
set "DEST_DIR=%~2"
copy /Y "%SRC%" "%DEST_DIR%\" >nul 2>&1
if errorlevel 1 (
  echo ERROR: Failed to copy %~nx1 to %DEST_DIR% — close the running installer and retry
  exit /b 1
)
echo Copied %~nx1 -^> %DEST_DIR%
exit /b 0
