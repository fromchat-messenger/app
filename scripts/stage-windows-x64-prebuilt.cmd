@echo off
setlocal EnableExtensions
if "%~1"=="" (
  echo Usage: stage-windows-x64-prebuilt.cmd ^<path-to-FromChat-app-image^>
  echo Example: stage-windows-x64-prebuilt.cmd C:\Downloads\FromChat
  exit /b 1
)
set "SRC=%~1"
if not exist "%SRC%\FromChat.exe" (
  echo Expected FromChat.exe in %SRC%
  exit /b 1
)
cd /d "%~dp0.."
set "DEST=app\desktop\build\prebuilt\windows-x64\app\FromChat"
if exist app\desktop\build\prebuilt\windows-x64 rmdir /s /q app\desktop\build\prebuilt\windows-x64
mkdir "%DEST%"
xcopy /E /I /Y "%SRC%\*" "%DEST%\"
echo Staged x64 app image at %DEST%
exit /b 0
