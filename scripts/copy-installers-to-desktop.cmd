@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "DIST=C:\FromChat\app\app\desktop\build\distributions\release"

if not exist "%DIST%\" (
  echo ERROR: Release output folder missing: %DIST%
  exit /b 1
)

set "WIN_DESKTOP="
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

if not exist "%WIN_DESKTOP%\" (
  echo ERROR: Could not resolve Windows Desktop path
  exit /b 1
)

set "INSTALLER="
for /f "delims=" %%F in ('dir /b /od "%DIST%\FromChat-Setup-*.exe" 2^>nul') do set "INSTALLER=%DIST%\%%F"

if not exist "!INSTALLER!" (
  echo ERROR: No FromChat-Setup-*.exe in %DIST%
  exit /b 1
)

for %%I in ("!INSTALLER!") do set "NAME=%%~nxI"
copy /y "!INSTALLER!" "%WIN_DESKTOP%\!NAME!" >nul
if errorlevel 1 (
  echo ERROR: Failed to copy to %WIN_DESKTOP%\!NAME!
  exit /b 1
)

echo Copied installer to Windows Desktop: %WIN_DESKTOP%\!NAME!
exit /b 0
