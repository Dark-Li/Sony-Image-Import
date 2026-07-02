param(
    [string]$JavaHome = "D:\Software\Android Studio\jbr",
    [string]$AndroidHome = "D:\Software\AndroidSDK",
    [string]$AdbPath = "D:\Software\scrcpy-win64-v4.0\adb.exe",
    [string]$GradleBat = "C:\Users\N.k\.gradle\wrapper\dists\gradle-8.14-bin\38aieal9i53h9rfe7vjup95b9\gradle-8.14\bin\gradle.bat",
    [string]$PackageName = "com.codex.sonyedge",
    [string]$ActivityName = ".ComposeMainActivity",
    [int]$LogSeconds = 60,
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$apkPath = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$logDir = Join-Path $repoRoot "build\device-logs"
$screenDir = Join-Path $repoRoot "build\device-screenshots"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$logPath = Join-Path $logDir "sonyedge-$timestamp.log"
$screenPath = Join-Path $screenDir "sonyedge-$timestamp.png"

$env:JAVA_HOME = $JavaHome
$env:ANDROID_HOME = $AndroidHome
$env:ANDROID_SDK_ROOT = $AndroidHome
$env:Path = "$JavaHome\bin;$AndroidHome\platform-tools;$env:Path"

if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}
if (-not (Test-Path $screenDir)) {
    New-Item -ItemType Directory -Path $screenDir | Out-Null
}

Write-Host "Checking connected Android devices..."
$devices = & $AdbPath devices -l
$devices | Write-Host
$readyDevices = @($devices | Select-String -Pattern "\sdevice\s" | ForEach-Object { $_.Line })
if ($readyDevices.Count -eq 0) {
    throw "No authorized ADB device found. Connect the phone, enable USB debugging, and accept the authorization dialog."
}
if ($readyDevices.Count -gt 1) {
    throw "Multiple ADB devices found. Disconnect extra devices or set ANDROID_SERIAL before running this script."
}

if (-not $SkipBuild) {
    Write-Host "Building debug APK..."
    Push-Location $repoRoot
    try {
        & $GradleBat :app:assembleDebug
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $apkPath)) {
    throw "APK not found: $apkPath"
}

Write-Host "Installing APK: $apkPath"
& $AdbPath install -r $apkPath

Write-Host "Launching $PackageName/$ActivityName"
& $AdbPath shell am force-stop $PackageName | Out-Null
& $AdbPath shell am start -n "$PackageName/$ActivityName" | Out-Null

Start-Sleep -Seconds 2
Write-Host "Capturing screenshot..."
& $AdbPath shell screencap -p /sdcard/sonyedge-screen.png | Out-Null
& $AdbPath pull /sdcard/sonyedge-screen.png $screenPath | Out-Null

Write-Host "Collecting logcat for $LogSeconds seconds..."
& $AdbPath logcat -c
$logcat = Start-Process -FilePath $AdbPath -ArgumentList @(
    "logcat",
    "-v", "time",
    "$PackageName`:V",
    "AndroidRuntime:E",
    "ActivityManager:I",
    "*:S"
) -NoNewWindow -RedirectStandardOutput $logPath -PassThru

Start-Sleep -Seconds $LogSeconds
if (-not $logcat.HasExited) {
    $logcat.Kill()
    $logcat.WaitForExit()
}

Write-Host "Device log saved to: $logPath"
Write-Host "Screenshot saved to: $screenPath"
Write-Host "Done."
