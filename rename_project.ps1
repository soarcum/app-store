# 💡 Android Project Renamer & Customizer (PowerShell AI-Ready Remapper)
# Allows users or AI to customize package names, app names, icons, and clean redundant directories safely!

param(
    [string]$NewPackage,
    [string]$NewAppName,
    [string]$IconPath
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " 🚀 Android Boilerplate Project Customizer" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

$TargetDir = Get-Location

# 1. Dynamically parse the old package name and old app name from config.gradle
$ConfigFile = Join-Path $TargetDir "config.gradle"
$OldPackage = ""
$OldAppName = ""

if (Test-Path $ConfigFile) {
    $ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8
    
    # Use PowerShell native -match and $Matches to avoid regex parser conflicts
    if ($ConfigContent -match 'applicationId\s*=\s*"(.+?)"') {
        $OldPackage = $Matches[1].Trim()
    }
    if ($ConfigContent -match 'appName\s*=\s*"(.+?)"') {
        $OldAppName = $Matches[1].Trim()
    }
}

# Fallback default values
if (-not $OldPackage -or $OldPackage.Trim() -eq "") { $OldPackage = "com.slowpack.androidtemplate" }
if (-not $OldAppName -or $OldAppName.Trim() -eq "") { $OldAppName = "android-template" }

# 2. Interactive prompt if params are not specified
if (-not $NewPackage -or $NewPackage.Trim() -eq "") {
    $NewPackage = Read-Host "Please enter the new package name (e.g. com.example.app) [Current: $OldPackage]"
}
if (-not $NewAppName -or $NewAppName.Trim() -eq "") {
    $NewAppName = Read-Host "Please enter the new application display name (e.g. My App) [Current: $OldAppName]"
}

if (-not $NewPackage -or -not $NewAppName -or $NewPackage.Trim() -eq "" -or $NewAppName.Trim() -eq "") {
    Write-Host "❌ Error: Inputs cannot be empty!" -ForegroundColor Red
    Exit
}

# Validate package name format
$PackageRegex = "^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$"
if ($NewPackage -notmatch $PackageRegex) {
    Write-Host "❌ Error: Invalid package name format!" -ForegroundColor Red
    Exit
}

Write-Host "`n⏳ Customizing your project globally, please wait..." -ForegroundColor Yellow
Write-Host "👉 Detected old package: $OldPackage" -ForegroundColor Gray
Write-Host "👉 Detected old app name: $OldAppName" -ForegroundColor Gray

# Check if package is actually changing to avoid self-copy IOExceptions
$PackageChanged = $true
if ($NewPackage -eq $OldPackage) {
    $PackageChanged = $false
    Write-Host "👉 New package is identical to old package. Skipping source package refactoring." -ForegroundColor Yellow
}

$OldPackagePath = $OldPackage.Replace(".", "/")
$NewPackagePath = $NewPackage.Replace(".", "/")

# 3. Replace package references globally
if ($PackageChanged) {
    Write-Host "👉 Step 1/5: Replacing package name references in source code..." -ForegroundColor Gray
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
} else {
    Write-Host "👉 Step 1/5: Package unchanged. Skipped references replacement." -ForegroundColor Gray
}

# 4. Update config.gradle configuration center
Write-Host "👉 Step 2/5: Updating config.gradle configuration center..." -ForegroundColor Gray
if (Test-Path $ConfigFile) {
    $ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8
    $ConfigContent = $ConfigContent -replace 'applicationId\s*=\s*".*"', ('applicationId = "' + $NewPackage + '"')
    $ConfigContent = $ConfigContent -replace 'appName\s*=\s*".*"', ('appName = "' + $NewAppName + '"')
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($ConfigFile, $ConfigContent, $Utf8NoBom)
}

# 5. Sync strings.xml display name
Write-Host "👉 Step 3/5: Updating strings.xml application display name..." -ForegroundColor Gray
$StringsFile = Join-Path $TargetDir "app/src/main/res/values/strings.xml"
if (Test-Path $StringsFile) {
    $StringsContent = Get-Content -Path $StringsFile -Raw -Encoding UTF8
    $StringsContent = $StringsContent -replace '<string name="app_name">(.+?)</string>', ('<string name="app_name">' + $NewAppName + '</string>')
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($StringsFile, $StringsContent, $Utf8NoBom)
}

# 6. Configure and mount adaptive application icon
if (-not $IconPath -or $IconPath.Trim() -eq "") {
    # Skip icon customization since no path was provided
} elseif (Test-Path $IconPath) {
    Write-Host "👉 Step 3.5: Custom icon path detected, setting up adaptive icon..." -ForegroundColor Gray
    $DrawableDir = Join-Path $TargetDir "app/src/main/res/drawable"
    if (-not (Test-Path $DrawableDir)) {
        New-Item -ItemType Directory -Force -Path $DrawableDir | Out-Null
    }
    
    $TargetIconPath = Join-Path $DrawableDir "ic_launcher_foreground.png"
    Copy-Item -Path $IconPath -Destination $TargetIconPath -Force
    Write-Host "   ✔ Icon file successfully copied to: $TargetIconPath" -ForegroundColor Green

    # Rewrite adaptive icon XML configurations
    $MipmapDir = Join-Path $TargetDir "app/src/main/res/mipmap-anydpi-v26"
    if (-not (Test-Path $MipmapDir)) {
        New-Item -ItemType Directory -Force -Path $MipmapDir | Out-Null
    }

    $LauncherXml = '<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@android:color/white" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>'

    Set-Content -Path (Join-Path $MipmapDir "ic_launcher.xml") -Value $LauncherXml -Encoding UTF8
    Set-Content -Path (Join-Path $MipmapDir "ic_launcher_round.xml") -Value $LauncherXml -Encoding UTF8
    Write-Host "   ✔ Adaptive icon XML configuration files updated successfully!" -ForegroundColor Green
}

# 7. Refactor physical package directory paths
if ($PackageChanged) {
    Write-Host "👉 Step 4/5: Refactoring physical java package directories..." -ForegroundColor Gray
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
                # Create new package directory structure
                New-Item -ItemType Directory -Force -Path $NewFullFolder | Out-Null
                
                # Move all source files to the new folder
                Copy-Item -Path "$OldFullFolder\*" -Destination $NewFullFolder -Recurse -Force
                
                # Remove old directory
                Remove-Item -Path $OldFullFolder -Recurse -Force
                
                # Recursively clean old empty parent directories up to base java dir
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
} else {
    Write-Host "👉 Step 4/5: Package unchanged. Skipped physical directories refactoring." -ForegroundColor Gray
}

# 8. Clear local gradle cache
Write-Host "👉 Step 5/5: Clearing local Gradle build cache..." -ForegroundColor Gray
$BuildDir = Join-Path $TargetDir "app/build"
if (Test-Path $BuildDir) {
    Remove-Item -Path $BuildDir -Recurse -Force
}

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host " 🎉 Congratulations! Customization Completed!" -ForegroundColor Green
Write-Host " Package name changed to: $NewPackage" -ForegroundColor Green
Write-Host " Display name changed to: $NewAppName" -ForegroundColor Green
Write-Host " You can now import the project with Android Studio." -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green

# Wait for exit if running interactively
if (-not $NewPackage -or -not $NewAppName -or $NewPackage.Trim() -eq "" -or $NewAppName.Trim() -eq "") {
    Read-Host "Press Enter to exit..."
}
