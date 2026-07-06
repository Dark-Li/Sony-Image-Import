param(
    [ValidateSet("patch", "minor", "major")]
    [string]$Part = "patch"
)

$ErrorActionPreference = "Stop"

$projectRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$gradleFile = Join-Path $projectRoot "app\build.gradle"
$content = Get-Content -Raw -Path $gradleFile

$codeMatch = [regex]::Match($content, 'versionCode\s+(\d+)')
$nameMatch = [regex]::Match($content, 'versionName\s+"(\d+)\.(\d+)\.(\d+)"')

if (-not $codeMatch.Success -or -not $nameMatch.Success) {
    throw "Could not find versionCode and semantic versionName in app\build.gradle."
}

$nextCode = [int]$codeMatch.Groups[1].Value + 1
$major = [int]$nameMatch.Groups[1].Value
$minor = [int]$nameMatch.Groups[2].Value
$patch = [int]$nameMatch.Groups[3].Value

switch ($Part) {
    "major" {
        $major += 1
        $minor = 0
        $patch = 0
    }
    "minor" {
        $minor += 1
        $patch = 0
    }
    default {
        $patch += 1
    }
}

$nextName = "$major.$minor.$patch"
$content = [regex]::Replace($content, 'versionCode\s+\d+', "versionCode $nextCode", 1)
$content = [regex]::Replace($content, 'versionName\s+"\d+\.\d+\.\d+"', "versionName `"$nextName`"", 1)

[System.IO.File]::WriteAllText(
    $gradleFile,
    $content,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "Updated app version to versionCode=$nextCode versionName=$nextName"
