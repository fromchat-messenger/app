@echo off
setlocal EnableExtensions
set "OUT_DIR=%~1"
set "SRC=%~2"
set "JAVA_HOME=%~3"
set "ARCH=%~4"

if /I "%ARCH%"=="arm64" (
  set "VCVARS_NAME=vcvarsarm64.bat"
) else (
  set "VCVARS_NAME=vcvars64.bat"
)

set "VCVARS="
if exist "C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\%VCVARS_NAME%" (
  set "VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\%VCVARS_NAME%"
)
if not defined VCVARS if exist "C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\%VCVARS_NAME%" (
  set "VCVARS=C:\Program Files\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\%VCVARS_NAME%"
)
if not defined VCVARS if exist "C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\%VCVARS_NAME%" (
  set "VCVARS=C:\Program Files\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\%VCVARS_NAME%"
)
if not defined VCVARS if exist "C:\Program Files (x86)\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\%VCVARS_NAME%" (
  set "VCVARS=C:\Program Files (x86)\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\%VCVARS_NAME%"
)
if not defined VCVARS if exist "C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\%VCVARS_NAME%" (
  set "VCVARS=C:\Program Files\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\%VCVARS_NAME%"
)
if not defined VCVARS (
  for /f "delims=" %%i in ('where /r "C:\Program Files\Microsoft Visual Studio" %VCVARS_NAME% 2^>nul') do set "VCVARS=%%i"
)
if not defined VCVARS (
  echo compile-windows-native: %VCVARS_NAME% not found >&2
  exit /b 1
)

call "%VCVARS%"
if errorlevel 1 exit /b 1

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
cd /d "%OUT_DIR%"

set "JNI_INCLUDE=%JAVA_HOME%\include"
set "JNI_WIN32=%JNI_INCLUDE%\win32"

cl /nologo /LD /EHsc /std:c++17 /utf-8 ^
  /I"%JNI_INCLUDE%" /I"%JNI_WIN32%" ^
  "%SRC%" /Fe:fromchat_windows.dll ^
  /link windowsapp.lib user32.lib shell32.lib ole32.lib oleaut32.lib uxtheme.lib
if errorlevel 1 (
  echo compile-windows-native: cl failed >&2
  exit /b 1
)
if not exist "fromchat_windows.dll" (
  echo compile-windows-native: missing fromchat_windows.dll in %OUT_DIR% >&2
  exit /b 1
)

if exist "fromchat_windows.lib" del "fromchat_windows.lib"
if exist "fromchat_windows.exp" del "fromchat_windows.exp"
if exist "WindowsNativeBridge.obj" del "WindowsNativeBridge.obj"
exit /b 0
