@echo off
setlocal EnableExtensions

rem Sync Mac workspace share -> local VM copy.
rem Uses /E (not /MIR) so VM-only build caches (target, build, .gradle) are never deleted.

set "SRC=C:\Mac\Home\Desktop\FromChat\app"
set "DEST=C:\FromChat\app"

if not exist "%SRC%\" (
  echo ERROR: Mac share not found at %SRC%
  echo Enable Parallels shared folders or adjust SRC in sync-windows-vm.cmd
  exit /b 1
)

if not exist "%DEST%\" mkdir "%DEST%"

set "OPTS=/E /R:2 /W:2 /NP /NFL /NDL"
set "CACHE=/XD target build .gradle node_modules .git"
set "SKIP=/XF gradle-daemon-jvm.properties"

if /I "%~1"=="purge-sources" (
  call :PurgeKotlinSources
  exit /b 0
)

if /I "%~1"=="rust" (
  echo Syncing app\desktop\windows-setup ^(keeping target\^)...
  robocopy "%SRC%\app\desktop\windows-setup" "%DEST%\app\desktop\windows-setup" %OPTS% /XD target
  goto :finish
)

if /I "%~1"=="scripts" (
  echo Syncing scripts...
  robocopy "%SRC%\scripts" "%DEST%\scripts" %OPTS%
  goto :finish
)

if /I "%~1"=="gradle" (
  call :PurgeKotlinSources
  echo Syncing app tree for Gradle ^(keeping build caches^)...
  robocopy "%SRC%" "%DEST%" %OPTS% %CACHE% %SKIP%
  goto :finish
)

echo Syncing full app tree ^(preserving target, build, .gradle^)...
call :PurgeKotlinSources
robocopy "%SRC%" "%DEST%" %OPTS% %CACHE% %SKIP%
goto :finish

:PurgeKotlinSources
rem Robocopy /E never deletes files removed on the Mac share; stale .kt paths break compile.
echo Purging VM Kotlin source trees before sync...
if exist "%DEST%\app\shared\src" rmdir /s /q "%DEST%\app\shared\src"
if exist "%DEST%\app\desktop\src" rmdir /s /q "%DEST%\app\desktop\src"
if exist "%DEST%\app\android\src" rmdir /s /q "%DEST%\app\android\src"
exit /b 0

:finish
set "RC=%ERRORLEVEL%"
rem Robocopy: 0-7 = success (0 = nothing to copy, 1+ = copied files)
if %RC% GEQ 8 (
  echo robocopy failed with exit code %RC%
  exit /b %RC%
)
exit /b 0
