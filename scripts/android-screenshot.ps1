param(
    [string]$AdbPath = "D:\Software\scrcpy-win64-v4.0\adb.exe",
    [string]$RemotePath = "/sdcard/sonyedge-screen.png"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$screenDir = Join-Path $repoRoot "build\device-screenshots"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$screenPath = Join-Path $screenDir "screen-$timestamp.png"

if (-not (Test-Path $screenDir)) {
    New-Item -ItemType Directory -Path $screenDir | Out-Null
}

$devices = & $AdbPath devices -l
$devices | Write-Host
$readyDevices = @($devices | Select-String -Pattern "\sdevice\s" | ForEach-Object { $_.Line })
if ($readyDevices.Count -eq 0) {
    throw "No authorized ADB device found."
}
if ($readyDevices.Count -gt 1) {
    throw "Multiple ADB devices found. Disconnect extra devices or set ANDROID_SERIAL before running this script."
}

& $AdbPath shell screencap -p $RemotePath | Out-Null
& $AdbPath pull $RemotePath $screenPath | Out-Null
Write-Host "Screenshot saved to: $screenPath"
