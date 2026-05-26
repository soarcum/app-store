# Create a new Android app from the blank template
# Usage: .\create_app.ps1 -AppName "Calculator" -PackageName "app.calculator" -OutputDir "C:\Users\Administrator\Desktop\cool\calculator" -GithubOwner "soarcum" -GithubRepo "calculator" -Description "..." -AccentColor "#4CAF50"
param(
    [string]$AppName,
    [string]$PackageName = "",
    [string]$OutputDir = "",
    [string]$GithubOwner = "soarcum",
    [string]$GithubRepo = "",
    [string]$IconPath = "",
    [string]$Description = "",
    [string]$AccentColor = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Create New App from Blank Template" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# Only AppName is required - everything else is auto-derived
if (-not $AppName -or $AppName.Trim() -eq "") {
    $AppName = Read-Host "Enter app name (e.g. Calculator)"
}
if (-not $AppName -or $AppName.Trim() -eq "") {
    Write-Host "Error: App name is required!" -ForegroundColor Red
    Exit
}

# Auto-derive all other parameters
$RepoName = $AppName.ToLower() -replace '\s+', '-'
if (-not $PackageName -or $PackageName.Trim() -eq "") { $PackageName = "com.soar.$RepoName" }
if (-not $OutputDir -or $OutputDir.Trim() -eq "") { $OutputDir = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "..\..\$RepoName" }
if (-not $GithubRepo -or $GithubRepo.Trim() -eq "") { $GithubRepo = $RepoName }
if (-not $Description -or $Description.Trim() -eq "") { $Description = "A clean and minimal $AppName app." }
if (-not $AccentColor -or $AccentColor.Trim() -eq "") { $AccentColor = "#2196F3" }

# Validate package name format
$PackageRegex = "^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$"
if ($PackageName -notmatch $PackageRegex) {
    Write-Host "Error: Invalid package name format: $PackageName" -ForegroundColor Red
    Exit
}

if (Test-Path $OutputDir) {
    Write-Host "Error: Output directory already exists: $OutputDir" -ForegroundColor Red
    Exit
}

# Get paths
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TemplateDir = Join-Path $ScriptDir "templates\blank-app"

if (-not (Test-Path $TemplateDir)) {
    Write-Host "Error: Template not found at: $TemplateDir" -ForegroundColor Red
    Exit
}

Write-Host "`nCreating app:" -ForegroundColor Yellow
Write-Host "  App Name:     $AppName" -ForegroundColor Gray
Write-Host "  Package:      $PackageName" -ForegroundColor Gray
Write-Host "  Output:       $OutputDir" -ForegroundColor Gray
Write-Host "  GitHub:       $GithubOwner/$GithubRepo" -ForegroundColor Gray
Write-Host "  Description:  $Description" -ForegroundColor Gray
Write-Host "  Accent Color: $AccentColor" -ForegroundColor Gray

# Step 1: Copy template
Write-Host "`nStep 1/5: Copying template..." -ForegroundColor Gray
Copy-Item -Path $TemplateDir -Destination $OutputDir -Recurse -Force
Write-Host "  Done." -ForegroundColor Green

# Step 2-4: Rename, update config, init git (all inside the new project dir)
Write-Host "Step 2/5: Renaming project..." -ForegroundColor Gray
Push-Location $OutputDir

try {
    $RenamePath = Join-Path $ScriptDir "rename_project.ps1"
    $RenameArgs = @("-ExecutionPolicy", "Bypass", "-File", $RenamePath,
        "-NewPackage", $PackageName,
        "-NewAppName", $AppName)
    if ($IconPath -and $IconPath.Trim() -ne "" -and (Test-Path $IconPath)) {
        $RenameArgs += "-IconPath"
        $RenameArgs += $IconPath
    }
    & powershell.exe @RenameArgs

    Write-Host "Step 3/5: Updating GitHub config..." -ForegroundColor Gray
    $ConfigFile = "config.gradle"
    $ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8
    $ConfigContent = $ConfigContent -replace 'githubOwner\s*=\s*"[^"]*"', "githubOwner = `"$GithubOwner`""
    $ConfigContent = $ConfigContent -replace 'githubRepo\s*=\s*"[^"]*"', "githubRepo = `"$GithubRepo`""
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText((Get-Item $ConfigFile).FullName, $ConfigContent, $Utf8NoBom)
    Write-Host "  Done." -ForegroundColor Green

    Write-Host "Step 4/5: Initializing git..." -ForegroundColor Gray
    git init
    git add .
    git commit -m "chore: initialize $AppName from blank template"
    Write-Host "  Done." -ForegroundColor Green

} finally {
    Pop-Location
}

# Step 5: Register in app store catalog
Write-Host "Step 5/5: Registering in app store..." -ForegroundColor Gray
$AppsJsonPath = Join-Path $ScriptDir "data\apps.json"
if (Test-Path $AppsJsonPath) {
    $AppsJson = Get-Content -Path $AppsJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $AppId = $AppName.ToLower() -replace '\s+', '-'
    $IconLetter = $AppName.Substring(0, 1).ToUpper()

    $NewEntry = [PSCustomObject]@{
        id           = $AppId
        name         = $AppName
        packageName  = $PackageName
        githubOwner  = $GithubOwner
        githubRepo   = $GithubRepo
        description  = $Description
        iconLetter   = $IconLetter
        accentColor  = $AccentColor
    }

    $AppsJson += $NewEntry
    $JsonOutput = $AppsJson | ConvertTo-Json -Depth 5
    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($AppsJsonPath, $JsonOutput, $Utf8NoBom)
    Write-Host "  Registered as '$AppId' in data/apps.json" -ForegroundColor Green
} else {
    Write-Host "  Warning: data/apps.json not found, skipping." -ForegroundColor Yellow
}

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host "  App '$AppName' Created Successfully!" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Location: $OutputDir" -ForegroundColor White
Write-Host "  Package:  $PackageName" -ForegroundColor White
Write-Host ""
