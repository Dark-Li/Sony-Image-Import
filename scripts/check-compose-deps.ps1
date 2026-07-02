param(
    [string]$GradleCache = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1",
    [string]$LocalMaven = "$PSScriptRoot\..\local-maven"
)

$ErrorActionPreference = "Stop"

$modules = @(
    @{ Group = "androidx.activity"; Name = "activity-compose"; Version = "1.9.3" },
    @{ Group = "androidx.compose.ui"; Name = "ui"; Version = "1.7.5" },
    @{ Group = "androidx.compose.ui"; Name = "ui-tooling"; Version = "1.7.5" },
    @{ Group = "androidx.compose.ui"; Name = "ui-tooling-preview"; Version = "1.7.5" },
    @{ Group = "androidx.compose.foundation"; Name = "foundation"; Version = "1.7.5" },
    @{ Group = "androidx.compose.material3"; Name = "material3"; Version = "1.3.1" },
    @{ Group = "androidx.compose.material"; Name = "material-icons-extended"; Version = "1.7.5" }
)

function Test-GradleCacheModule($module) {
    $path = Join-Path $GradleCache (Join-Path $module.Group (Join-Path $module.Name $module.Version))
    return Test-Path -LiteralPath $path
}

function Test-LocalMavenModule($module) {
    $groupPath = $module.Group.Replace(".", [IO.Path]::DirectorySeparatorChar)
    $path = Join-Path $LocalMaven (Join-Path $groupPath (Join-Path $module.Name $module.Version))
    return Test-Path -LiteralPath $path
}

$missing = @()
foreach ($module in $modules) {
    $coordinate = "$($module.Group):$($module.Name):$($module.Version)"
    $found = (Test-GradleCacheModule $module) -or (Test-LocalMavenModule $module)
    if ($found) {
        Write-Host "[OK]      $coordinate"
    } else {
        Write-Host "[MISSING] $coordinate"
        $missing += $coordinate
    }
}

if ($missing.Count -gt 0) {
    Write-Host ""
    Write-Host "Missing Compose dependencies. See docs/compose-dependency-setup.md"
    exit 1
}

Write-Host ""
Write-Host "All primary Compose dependencies are present."
