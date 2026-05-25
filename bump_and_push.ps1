# === Local Bump & Auto-Deployer Script ===
# Automatically bumps version and pushes to GitHub to trigger Actions CI release.

param (
    [string]$Notes = "" # Optional release notes to skip Read-Host for AI workflow
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Android Boilerplate Auto-Deploy Tool" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. Environment pre-checks
if (-not (Test-Path ".git")) {
    Write-Host "[ERROR] Git repository not initialized!" -ForegroundColor Red
    Write-Host "[INFO] Run 'git init' and set remote origin first." -ForegroundColor Yellow
    if ([string]::IsNullOrEmpty($Notes)) { Read-Host "Press Enter to exit..." }
    Exit
}

$ConfigFile = "config.gradle"
if (-not (Test-Path $ConfigFile)) {
    Write-Host "[ERROR] Global configuration file $ConfigFile not found!" -ForegroundColor Red
    Exit
}

# 2. Extract current version metadata
$ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8

$VersionCodeMatch = [regex]::Match($ConfigContent, 'versionCode\s*=\s*(\d+)')
$VersionNameMatch = [regex]::Match($ConfigContent, 'versionName\s*=\s*"([^"]+)"')

if (-not $VersionCodeMatch.Success -or -not $VersionNameMatch.Success) {
    Write-Host "[ERROR] Failed to parse version fields in $ConfigFile!" -ForegroundColor Red
    Exit
}

[int]$OldCode = $VersionCodeMatch.Groups[1].Value
$OldName = $VersionNameMatch.Groups[1].Value

# 3. Calculate new version code and name
$NewCode = $OldCode + 1
$NameParts = $OldName.Split('.')
if ($NameParts.Length -ge 3) {
    [int]$Patch = $NameParts[2]
    $NewPatch = $Patch + 1
    $NewName = "$($NameParts[0]).$($NameParts[1]).$NewPatch"
} else {
    $NewName = "$OldName.1"
}

Write-Host "=== Version Bump Detection ===" -ForegroundColor Gray
Write-Host "   Current Version: v$OldName (Code: $OldCode)" -ForegroundColor Gray
Write-Host "   Upgrading To   : v$NewName (Code: $NewCode)" -ForegroundColor Yellow

# 4. Gather Release Notes (skip prompt if param exists)
$ReleaseNotes = ""
if (-not [string]::IsNullOrEmpty($Notes)) {
    $ReleaseNotes = $Notes
    Write-Host "[INFO] Automatically using provided release notes: $ReleaseNotes" -ForegroundColor Yellow
} else {
    Write-Host ""
    $ReleaseNotes = Read-Host "馃摑 Enter release notes description (Optional, press Enter for default)"
    if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        $ReleaseNotes = "General optimization and stability improvements."
    }
}

# 5. Write updated versions back to config.gradle
Write-Host "Updating version fields in $ConfigFile..." -ForegroundColor Gray
$NewConfigContent = $ConfigContent -replace 'versionCode\s*=\s*\d+', "versionCode = $NewCode"
$NewConfigContent = $NewConfigContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$NewName`""
Set-Content -Path $ConfigFile -Value $NewConfigContent -Encoding UTF8

try {
    # 6. Git staging and commit transaction
    Write-Host "Staging local modifications and committing transaction..." -ForegroundColor Gray
    git add .
    
    $CommitMsg = "chore(release): bump version to v$NewName [code $NewCode]"
    git commit -m $CommitMsg -m $ReleaseNotes
    
    # 7. Create Git tags
    Write-Host "Tagging local repository with tag v$NewName..." -ForegroundColor Gray
    git tag -a "v$NewName" -m "Version v$NewName" -m $ReleaseNotes

    # 8. Push changes and tags to GitHub
    Write-Host "Pushing code commits and version tags to GitHub main branch..." -ForegroundColor Yellow
    Write-Host "[INFO] Triggering GitHub Actions CI/CD to build signed APKs in 2 minutes..." -ForegroundColor Yellow
    
    git push origin main
    git push origin --tags

    Write-Host "=========================================" -ForegroundColor Green
    Write-Host "  One-click Deploy Successfully Completed!" -ForegroundColor Green
    Write-Host "  Cloud CI/CD building pipeline activated." -ForegroundColor Green
    Write-Host "  New APK will be pushed to your Android phone in 2 minutes!" -ForegroundColor Green
    Write-Host "=========================================" -ForegroundColor Green

} catch {
    Write-Host "[ERROR] Git transaction failed with critical error! Terminating process." -ForegroundColor Red
    Write-Host "[INFO] Make sure your Git author user/email are configured and origin main exists." -ForegroundColor Yellow
    
    # Rollback version bump in config.gradle upon failure
    Set-Content -Path $ConfigFile -Value $ConfigContent -Encoding UTF8
    Write-Host "Rollback completed: config.gradle has been restored." -ForegroundColor Gray
}

# Block exiting for manual interactive mode
if ([string]::IsNullOrEmpty($Notes)) {
    Read-Host "Press Enter to exit..."
}
