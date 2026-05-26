# Android Project Renamer & Customizer
param(
    [string]$NewPackage,
    [string]$NewAppName,
    [string]$IconPath
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " Android Blank App Customizer" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$TargetDir = Get-Location

# 1. Parse old package name from config.gradle
$ConfigFile = Join-Path $TargetDir "config.gradle"
$OldPackage = ""
$OldAppName = ""

if (Test-Path $ConfigFile) {
    $ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8
    if ($ConfigContent -match 'applicationId\s*=\s*"(.+?)"') {
        $OldPackage = $Matches[1].Trim()
    }
    if ($ConfigContent -match 'appName\s*=\s*"(.+?)"') {
        $OldAppName = $Matches[1].Trim()
    }
}

if (-not $OldPackage -or $OldPackage.Trim() -eq "") { $OldPackage = "com.slowpack.blankapp" }
if (-not $OldAppName -or $OldAppName.Trim() -eq "") { $OldAppName = "BlankApp" }

# 2. Interactive prompt if params not specified
if (-not $NewPackage -or $NewPackage.Trim() -eq "") {
    $NewPackage = Read-Host "Enter new package name (e.g. com.example.app) [Current: $OldPackage]"
}
if (-not $NewAppName -or $NewAppName.Trim() -eq "") {
    $NewAppName = Read-Host "Enter new app display name (e.g. My App) [Current: $OldAppName]"
}

if (-not $NewPackage -or -not $NewAppName -or $NewPackage.Trim() -eq "" -or $NewAppName.Trim() -eq "") {
    Write-Host "Error: Inputs cannot be empty!" -ForegroundColor Red
    Exit
}

$PackageRegex = "^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$"
if ($NewPackage -notmatch $PackageRegex) {
    Write-Host "Error: Invalid package name format!" -ForegroundColor Red
    Exit
}

Write-Host "`nCustomizing project..." -ForegroundColor Yellow
Write-Host "Old package: $OldPackage" -ForegroundColor Gray
Write-Host "Old app name: $OldAppName" -ForegroundColor Gray

$PackageChanged = $true
if ($NewPackage -eq $OldPackage) {
    $PackageChanged = $false
    Write-Host "Package unchanged. Skipping source refactoring." -ForegroundColor Yellow
}

$OldPackagePath = $OldPackage.Replace(".", "/")
$NewPackagePath = $NewPackage.Replace(".", "/")

# 3. Replace package references globally
if ($PackageChanged) {
    Write-Host "Step 1/5: Replacing package references..." -ForegroundColor Gray
    $FileFilters = @("*.kt", "*.xml", "*.gradle", "*.properties")
    $FilesToProcess = Get-ChildItem -Path $TargetDir -Recurse -Include $FileFilters | Where-Object { $_.FullName -notmatch "\\build\\" -and $_.FullName -notmatch "\\\.git\\" -and $_.FullName -notmatch "\\\.gradle\\" }

    foreach ($File in $FilesToProcess) {
        $Content = Get-Content -Path $File.FullName -Raw -Encoding UTF8
        $Modified = $false
        if ($Content -match $OldPackage) {
            $Content = $Content.Replace($OldPackage, $NewPackage)
            $Modified = $true
        }
        if ($Modified) {
            $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
            [System.IO.File]::WriteAllText($File.FullName, $Content, $Utf8NoBom)
        }
    }
}

# 4. Update config.gradle
Write-Host "Step 2/5: Updating config.gradle..." -ForegroundColor Gray
if (Test-Path $ConfigFile) {
    $ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8
    $ConfigContent = $ConfigContent -replace 'applicationId\s*=\s*".*"', ('applicationId = "' + $NewPackage + '"')
    $ConfigContent = $ConfigContent -replace 'appName\s*=\s*".*"', ('appName = "' + $NewAppName + '"')
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($ConfigFile, $ConfigContent, $Utf8NoBom)
}

# 5. Update strings.xml
Write-Host "Step 3/5: Updating strings.xml..." -ForegroundColor Gray
$StringsFile = Join-Path $TargetDir "app/src/main/res/values/strings.xml"
if (Test-Path $StringsFile) {
    $StringsContent = Get-Content -Path $StringsFile -Raw -Encoding UTF8
    $StringsContent = $StringsContent -replace '<string name="app_name">(.+?)</string>', ('<string name="app_name">' + $NewAppName + '</string>')
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($StringsFile, $StringsContent, $Utf8NoBom)
}

# 6. Configure adaptive icon
if ($IconPath -and $IconPath.Trim() -ne "" -and (Test-Path $IconPath)) {
    Write-Host "Step 3.5: Setting up adaptive icon..." -ForegroundColor Gray
    $DrawableDir = Join-Path $TargetDir "app/src/main/res/drawable"
    if (-not (Test-Path $DrawableDir)) {
        New-Item -ItemType Directory -Force -Path $DrawableDir | Out-Null
    }
    $TargetIconPath = Join-Path $DrawableDir "ic_launcher_foreground.png"
    Copy-Item -Path $IconPath -Destination $TargetIconPath -Force
    Write-Host "  Icon copied to: $TargetIconPath" -ForegroundColor Green
}

# 7. Refactor physical package directories
if ($PackageChanged) {
    Write-Host "Step 4/5: Refactoring package directories..." -ForegroundColor Gray
    $SourceBaseDirs = @(
        "app/src/main/java",
        "app/src/androidTest/java",
        "app/src/test/java"
    )

    foreach ($BaseDir in $SourceBaseDirs) {
        $FullBaseDir = Join-Path $TargetDir $BaseDir
        if (Test-Path $FullBaseDir) {
            $OldFullFolder = Join-Path $FullBaseDir $OldPackagePath
            $NewFullFolder = Join-Path $FullBaseDir $NewPackagePath

            if (Test-Path $OldFullFolder) {
                New-Item -ItemType Directory -Force -Path $NewFullFolder | Out-Null
                Copy-Item -Path "$OldFullFolder\*" -Destination $NewFullFolder -Recurse -Force
                Remove-Item -Path $OldFullFolder -Recurse -Force

                $ParentFolder = Split-Path $OldFullFolder -Parent
                while ($ParentFolder -ne $FullBaseDir -and (Test-Path $ParentFolder)) {
                    $RemainingChildren = Get-ChildItem -Path $ParentFolder
                    if ($RemainingChildren.Count -eq 0) {
                        Remove-Item -Path $ParentFolder -Force
                        $ParentFolder = Split-Path $ParentFolder -Parent
                    } else {
                        break
                    }
                }
            }
        }
    }
}

# 8. Clear build cache
Write-Host "Step 5/5: Clearing build cache..." -ForegroundColor Gray
$BuildDir = Join-Path $TargetDir "app/build"
if (Test-Path $BuildDir) {
    Remove-Item -Path $BuildDir -Recurse -Force
}

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host " Customization Complete!" -ForegroundColor Green
Write-Host " Package: $NewPackage" -ForegroundColor Green
Write-Host " App Name: $NewAppName" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
