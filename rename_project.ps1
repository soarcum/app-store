# 💡 Android 项目一键极速重命名脚本 (Local PowerShell Remapper)
# 允许用户在 Fork 模板后，一键重组物理代码包名、替换代码引用、更新应用名并清理冗余！

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " 🚀 Android Boilerplate 一键项目初始化重命名工具" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. 引导交互输入
$NewPackage = Read-Host "请输入新的应用程序包名 (例如 com.example.wallet)"
$NewAppName = Read-Host "请输入新的应用程序显示名称 (例如 每日记账)"

if ([string]::IsNullOrWhiteSpace($NewPackage) -or [string]::IsNullOrWhiteSpace($NewAppName)) {
    Write-Host "❌ 输入的内容不能为空，操作已取消。" -ForegroundColor Red
    Exit
}

# 验证包名格式
if ($NewPackage -notmatch "^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$") {
    Write-Host "❌ 包名格式不合法，必须是由点分割的字母数字组合 (如 com.foo.bar)！" -ForegroundColor Red
    Exit
}

Write-Host "`n⏳ 正在为您进行项目全局深度重构，请稍候..." -ForegroundColor Yellow

$OldPackage = "com.template.app"
$OldPackagePath = "com/template/app"
$NewPackagePath = $NewPackage.Replace(".", "/")

$TargetDir = Get-Location

# 2. 批量替换文本内容中的包名和应用名称
Write-Host "👉 步骤 1/4: 正在进行代码全局包引用和常量替换..." -ForegroundColor Gray

# 需要替换的文件类型
$FileFilters = @("*.kt", "*.xml", "*.gradle", "*.properties")
$FilesToProcess = Get-ChildItem -Path $TargetDir -Recurse -Include $FileFilters | Where-Object { $_.FullName -notmatch "\\build\\" -and $_.FullName -notmatch "\\\.git\\" -and $_.FullName -notmatch "\\\.gradle\\" }

foreach ($File in $FilesToProcess) {
    $Content = Get-Content -Path $File.FullName -Raw -Encoding UTF8
    if ($Content -match $OldPackage) {
        $Content = $Content.Replace($OldPackage, $NewPackage)
        Set-Content -Path $File.FullName -Value $Content -Encoding UTF8
    }
}

# 3. 动态更新 config.gradle 配置中心
Write-Host "👉 步骤 2/4: 正在更新 config.gradle 配置中心..." -ForegroundColor Gray
$ConfigFile = Join-Path $TargetDir "config.gradle"
if (Test-Path $ConfigFile) {
    $ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8
    $ConfigContent = $ConfigContent -replace 'applicationId = ".*"', ('applicationId = "' + $NewPackage + '"')
    $ConfigContent = $ConfigContent -replace 'appName = ".*"', ('appName = "' + $NewAppName + '"')
    Set-Content -Path $ConfigFile -Value $ConfigContent -Encoding UTF8
}

# 4. 重构物理文件夹路径 (核心魔法)
Write-Host "👉 步骤 3/4: 正在重构 Android 项目物理包目录路径..." -ForegroundColor Gray

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
            # 创建新的深层目录结构
            New-Item -ItemType Directory -Force -Path $NewFullFolder | Out-Null
            
            # 搬运所有源码文件及子文件夹
            Copy-Item -Path "$OldFullFolder\*" -Destination $NewFullFolder -Recurse -Force
            
            # 清理旧有的物理路径
            Remove-Item -Path $OldFullFolder -Recurse -Force
            
            # 清理旧的空父目录 (com/template)
            $TemplateParent = Split-Path $OldFullFolder -Parent
            if (Test-Path $TemplateParent) {
                $RemainingChildren = Get-ChildItem -Path $TemplateParent
                if ($RemainingChildren.Count -eq 0) {
                    Remove-Item -Path $TemplateParent -Force
                }
            }
            $ComParent = Split-Path $TemplateParent -Parent
            if (Test-Path $ComParent) {
                $RemainingChildren = Get-ChildItem -Path $ComParent
                if ($RemainingChildren.Count -eq 0) {
                    Remove-Item -Path $ComParent -Force
                }
            }
        }
    }
}

# 5. 清理本地 Gradle 编译缓存，避免混淆
Write-Host "👉 步骤 4/4: 正在清除本地 Gradle 缓存，确保下一次编译干爽洁净..." -ForegroundColor Gray
$BuildDir = Join-Path $TargetDir "app/build"
if (Test-Path $BuildDir) {
    Remove-Item -Path $BuildDir -Recurse -Force
}

Write-Host "`n=========================================" -ForegroundColor Green
Write-Host " 🎉 恭喜您，项目重构重命名圆满完成！" -ForegroundColor Green
Write-Host " 包名已成功修改为: $NewPackage" -ForegroundColor Green
Write-Host " 应用显示名已变更为: $NewAppName" -ForegroundColor Green
Write-Host " 现在您可以用 Android Studio 重新导入并直接编写业务代码了！" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Green
Read-Host "按回车键退出脚本..."
