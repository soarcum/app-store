# Bump version and push to GitHub to trigger CI/CD release
param (
    [string]$Notes = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Auto-Deploy Tool" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

if (-not (Test-Path ".git")) {
    Write-Host "[ERROR] Git repository not initialized!" -ForegroundColor Red
    if ([string]::IsNullOrEmpty($Notes)) { Read-Host "Press Enter to exit..." }
    Exit
}

$ConfigFile = "config.gradle"
if (-not (Test-Path $ConfigFile)) {
    Write-Host "[ERROR] Config file $ConfigFile not found!" -ForegroundColor Red
    Exit
}

# Extract current version
$ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8
$VersionCodeMatch = [regex]::Match($ConfigContent, 'versionCode\s*=\s*(\d+)')
$VersionNameMatch = [regex]::Match($ConfigContent, 'versionName\s*=\s*"([^"]+)"')

if (-not $VersionCodeMatch.Success -or -not $VersionNameMatch.Success) {
    Write-Host "[ERROR] Failed to parse version fields!" -ForegroundColor Red
    Exit
}

[int]$OldCode = $VersionCodeMatch.Groups[1].Value
$OldName = $VersionNameMatch.Groups[1].Value

# Calculate new version
$NewCode = $OldCode + 1
$NameParts = $OldName.Split('.')
if ($NameParts.Length -ge 3) {
    [int]$Patch = $NameParts[2]
    $NewPatch = $Patch + 1
    $NewName = "$($NameParts[0]).$($NameParts[1]).$NewPatch"
} else {
    $NewName = "$OldName.1"
}

Write-Host "Version: v$OldName (Code: $OldCode) -> v$NewName (Code: $NewCode)" -ForegroundColor Yellow

# Release notes
$ReleaseNotes = ""
if (-not [string]::IsNullOrEmpty($Notes)) {
    $ReleaseNotes = $Notes
} else {
    $ReleaseNotes = Read-Host "Enter release notes (press Enter for default)"
    if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        $ReleaseNotes = "General optimization and stability improvements."
    }
}

# Update config.gradle
$NewConfigContent = $ConfigContent -replace 'versionCode\s*=\s*\d+', "versionCode = $NewCode"
$NewConfigContent = $NewConfigContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$NewName`""

$Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Get-Item $ConfigFile).FullName, $NewConfigContent, $Utf8NoBom)

try {
    git add .
    $CommitMsg = "chore(release): bump version to v$NewName [code $NewCode]"
    git commit -m $CommitMsg -m $ReleaseNotes
    git tag -a "v$NewName" -m "Version v$NewName" -m $ReleaseNotes

    git push origin main
    git push origin --tags

    Write-Host "=========================================" -ForegroundColor Green
    Write-Host "  Deploy Successful!" -ForegroundColor Green
    Write-Host "  CI/CD pipeline activated." -ForegroundColor Green
    Write-Host "=========================================" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Git push failed!" -ForegroundColor Red
    [System.IO.File]::WriteAllText((Get-Item $ConfigFile).FullName, $ConfigContent, $Utf8NoBom)
    Write-Host "Rollback completed: config.gradle restored." -ForegroundColor Gray
}

if ([string]::IsNullOrEmpty($Notes)) {
    Read-Host "Press Enter to exit..."
}
