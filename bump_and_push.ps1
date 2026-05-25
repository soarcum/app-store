# 💡 本地一键版本自增并推送至云端打包脚本 (Local Bump & Auto-Deployer)
# 自动提升 versionCode 与 versionName 小版本，快速提交代码并推送，触发 GitHub 自动构建及推送更新

param (
    [string]$Notes = ""
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host " 🚀 Android Boilerplate 自动版本递增与一键发布工具" -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan

# 1. 安全检查：检测当前目录是否已经初始化了 Git
if (-not (Test-Path ".git")) {
    Write-Host "❌ 错误: 当前目录尚未初始化 Git 仓库，无法使用自动推送功能！" -ForegroundColor Red
    Write-Host "💡 提示: 请先在项目根目录运行 'git init' 并与 GitHub 远端仓库关联。" -ForegroundColor Yellow
    if ([string]::IsNullOrEmpty($Notes)) { Read-Host "按回车键退出..." }
    Exit
}

$ConfigFile = "config.gradle"
if (-not (Test-Path $ConfigFile)) {
    Write-Host "❌ 错误: 找不到全局配置文件 $ConfigFile！" -ForegroundColor Red
    Exit
}

# 2. 读取并提取当前版本信息
$ConfigContent = Get-Content -Path $ConfigFile -Raw -Encoding UTF8

$VersionCodeMatch = [regex]::Match($ConfigContent, 'versionCode\s*=\s*(\d+)')
$VersionNameMatch = [regex]::Match($ConfigContent, 'versionName\s*=\s*"([^"]+)"')

if (-not $VersionCodeMatch.Success -or -not $VersionNameMatch.Success) {
    Write-Host "❌ 无法从 $ConfigFile 中解析出有效的版本号配置！" -ForegroundColor Red
    Exit
}

[int]$OldCode = $VersionCodeMatch.Groups[1].Value
$OldName = $VersionNameMatch.Groups[1].Value

# 3. 自动计算新的版本号
# versionCode 直接 +1
$NewCode = $OldCode + 1

# versionName 递增最后一位小版本号 (如 1.0.4 -> 1.0.5)
$NameParts = $OldName.Split('.')
if ($NameParts.Length -ge 3) {
    [int]$Patch = $NameParts[2]
    $NewPatch = $Patch + 1
    $NewName = "$($NameParts[0]).$($NameParts[1]).$NewPatch"
} else {
    $NewName = "$OldName.1"
}

Write-Host "📈 本地版本检测:" -ForegroundColor Gray
Write-Host "   当前版本: v$OldName (Code: $OldCode)" -ForegroundColor Gray
Write-Host "   拟升级至: v$NewName (Code: $NewCode)" -ForegroundColor Yellow

# 4. 引导输入本次更新的发布日志
$ReleaseNotes = ""
if (-not [string]::IsNullOrEmpty($Notes)) {
    # 💡 如果有外部参数，直接使用，免去交互
    $ReleaseNotes = $Notes
    Write-Host "📝 自动采用传入的更新日志: $ReleaseNotes" -ForegroundColor Yellow
} else {
    Write-Host ""
    $ReleaseNotes = Read-Host "📝 请输入本次更新的日志说明 (可选，可直接回车)"
    if ([string]::IsNullOrWhiteSpace($ReleaseNotes)) {
        $ReleaseNotes = "常规功能优化与性能提升。"
    }
}

# 5. 更新并写入 config.gradle
Write-Host "`n⏳ 正在更新 $ConfigFile..." -ForegroundColor Gray
$NewConfigContent = $ConfigContent -replace 'versionCode\s*=\s*\d+', "versionCode = $NewCode"
$NewConfigContent = $NewConfigContent -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$NewName`""
Set-Content -Path $ConfigFile -Value $NewConfigContent -Encoding UTF8

try {
    # 6. 执行 Git 一键提交流程
    Write-Host "👉 正在暂存本地修改并提交事务..." -ForegroundColor Gray
    git add .
    
    $CommitMsg = "chore(release): bump version to v$NewName [code $NewCode]"
    git commit -m $CommitMsg -m $ReleaseNotes
    
    # 7. 打上带日志说明的 Git Tags
    Write-Host "👉 正在本地标记版本标签 v$NewName..." -ForegroundColor Gray
    git tag -a "v$NewName" -m "Version v$NewName" -m $ReleaseNotes

    # 8. 强力推送至 GitHub
    Write-Host "🚀 正在向 GitHub 主分支推送代码和 Tags..." -ForegroundColor Yellow
    Write-Host "💡 提示: 这将自动触发 GitHub Actions 构建签名 Release APK，2分钟后手机上就会检测到更新！" -ForegroundColor Yellow
    
    git push origin main
    git push origin --tags

    Write-Host "`n=========================================" -ForegroundColor Green
    Write-Host " 🎉 一键发布流程圆满成功！" -ForegroundColor Green
    Write-Host " 云端构建流水线已成功激活，请耐心等待 2 分钟左右。" -ForegroundColor Green
    Write-Host " 2分钟后直接在您手机上打开 App，即可点击更新享用新版！" -ForegroundColor Green
    Write-Host "=========================================" -ForegroundColor Green

} catch {
    Write-Host "`n❌ 运行 Git 命令时遇到严重错误，操作中断！" -ForegroundColor Red
    Write-Host "💡 请确保您已配置本地 Git 用户名与邮箱，且远端存在对应的 'main' 分支。" -ForegroundColor Yellow
    
    # 发生异常，回滚本地 config.gradle，防止版本号不一致
    Set-Content -Path $ConfigFile -Value $ConfigContent -Encoding UTF8
    Write-Host "🔄 全局配置文件 $ConfigFile 已自动回滚复原。" -ForegroundColor Gray
}

# 💡 如果没有传入 Notes 参数（说明是人工交互模式），等待回车后关闭窗口；否则直接退出，不阻塞
if ([string]::IsNullOrEmpty($Notes)) {
    Read-Host "按回车键退出..."
}

