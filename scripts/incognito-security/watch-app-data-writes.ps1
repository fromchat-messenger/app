# Fail on writes under the FromChat app data folder (run during incognito manual test).
param(
    [Parameter(Mandatory = $true)]
    [string] $AppDataRoot
)

$ErrorActionPreference = "Stop"
$AppDataRoot = (Resolve-Path -LiteralPath $AppDataRoot).Path

Write-Host "Watching for writes under: $AppDataRoot"
Write-Host "Start incognito session in the app, then stop this script with Ctrl+C if clean."

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $AppDataRoot
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents = $true

Register-ObjectEvent -InputObject $watcher -EventName Created -SourceIdentifier PlainWriteCreated -Action {
    Write-Error "PLAIN_WRITE_DETECTED path=$($Event.SourceEventArgs.FullPath)"
    exit 1
} | Out-Null

Register-ObjectEvent -InputObject $watcher -EventName Changed -SourceIdentifier PlainWriteChanged -Action {
    Write-Error "PLAIN_WRITE_DETECTED path=$($Event.SourceEventArgs.FullPath)"
    exit 1
} | Out-Null

try {
    while ($true) { Start-Sleep -Seconds 3600 }
}
finally {
    $watcher.Dispose()
}
