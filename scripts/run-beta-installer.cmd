@echo off
setlocal EnableExtensions EnableDelayedExpansion

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

if not defined WIN_DESKTOP (
  echo ERROR: Could not resolve Desktop path
  exit /b 1
)

set "INSTALLER="
for /f "delims=" %%F in ('dir /b /od "%WIN_DESKTOP%\FromChat-Setup-*-beta2.exe" 2^>nul') do set "INSTALLER=%WIN_DESKTOP%\%%F"

if not defined INSTALLER (
  set "DIST=C:\FromChat\app\app\desktop\build\distributions\release"
  for /f "delims=" %%F in ('dir /b /od "%DIST%\FromChat-Setup-*-beta2.exe" 2^>nul') do set "INSTALLER=%DIST%\%%F"
)

if not exist "%INSTALLER%" (
  echo ERROR: No FromChat-Setup-*-beta2.exe on Desktop or in dist folder
  exit /b 1
)

echo Launching installer: %INSTALLER%
set "TASK=FromChatBetaInstall"
schtasks /delete /tn "%TASK%" /f >nul 2>&1

for /f "tokens=1-2 delims=:" %%H in ("%TIME%") do set "ST=%%H:%%M"
set "ST=%ST: =0%"

if defined LOGON (
  schtasks /create /tn "%TASK%" /tr "\"%INSTALLER%\" --upgrade --launch" /sc once /st %ST% /ru "%LOGON%" /it /f
) else (
  schtasks /create /tn "%TASK%" /tr "\"%INSTALLER%\" --upgrade --launch" /sc once /st %ST% /f
)

schtasks /run /tn "%TASK%" >nul
ping -n 3 127.0.0.1 >nul
schtasks /delete /tn "%TASK%" /f >nul 2>&1
echo Installer started.
exit /b 0
