---
description: commit@build
---

# 🤖 AI 智能体提交并监听打包状态 (Commit and Build)

本工作流供协助您编码的 AI 智能体（Agent）调用。它用于自动暂存修改、增加版本号、推送至 GitHub 触发 Actions 构建，并自动监听构建状态。

如果打包失败，AI 智能体将**自动抓取并分析错误日志**以提出修复建议，构成完美的开发闭环。

---

## 步骤 1：自动递增版本号并一键推送

AI 智能体应通过执行本地的 `bump_and_push.ps1` 脚本，将您的本地修改暂存、自动计算自增版本号、提交 Git 事务打上标签并推送至远端。

> 💡 **AI 智能体须知**：
> 运行此脚本时必须带上 `-Notes` 参数（其内容为本次修改的简要总结），以激活“全自动无人值守模式”，防止触发终端的 Read-Host 阻塞。

// turbo
```powershell
# 1. AI 智能体在此输入本次更新的简要日志说明 (用于发布日志和 Git Commit/Tag 说明)
$Notes = "AI 自动提交：完成了通用空模板的全部核心框架与自动化工作流搭建。"

# 2. 启动全自动无人值守部署流程
powershell.exe -ExecutionPolicy Bypass -File .\bump_and_push.ps1 -Notes $Notes

# 3. 提取最新的 Git 提交 SHA
$lastSha = (git rev-parse HEAD).Trim()
$lastSha | Set-Content .last_commit_sha -NoNewline

Write-Host "✅ 本地一键推送已完成，最新 Commit SHA 为: $lastSha"
```

---

## 步骤 2：监听 GitHub 编译并启动故障自诊断

AI 智能体将在后台轮询 GitHub Actions API，监听针对刚才推送的 SHA 的云端构建状态。
**此工作流完全动态化：自动从 `config.gradle` 中解析出您的 GitHub 账号及仓库名，彻底告别硬编码！**

// turbo
```powershell
$ErrorActionPreference = "Stop"

# 1. 动态从项目唯一配置中心提取仓库信息
$configFile = "config.gradle"
if (-not (Test-Path $configFile)) {
    Write-Host "❌ 错误: 找不到全局配置文件 config.gradle！" -ForegroundColor Red
    Exit 1
}
$configContent = Get-Content $configFile -Raw -Encoding UTF8
$ownerMatch = [regex]::Match($configContent, 'githubOwner\s*=\s*"([^"]+)"')
$repoMatch = [regex]::Match($configContent, 'githubRepo\s*=\s*"([^"]+)"')

if (-not $ownerMatch.Success -or -not $repoMatch.Success) {
    Write-Host "❌ 错误: 无法从 config.gradle 中解析出有效仓库路径！" -ForegroundColor Red
    Exit 1
}

$owner = $ownerMatch.Groups[1].Value.Trim()
$repo = $repoMatch.Groups[1].Value.Trim()
$fullRepo = "$owner/$repo"

# 2. 检查本地是否有鉴权 Token
$tokenFile = ".github_token"
if (-not (Test-Path $tokenFile)) {
    Write-Host "⚠️ 未在根目录下检测到 $tokenFile 文件。" -ForegroundColor Yellow
    Write-Host "💡 提示: 公开仓库的 Actions Runs API 允许匿名访问，但由于每小时有60次限流上限，强烈建议您在根目录下创建一个 $tokenFile 文件（写入您的 GitHub Personal Access Token）以获得高稳定频次调用。" -ForegroundColor Yellow
    $token = ""
} else {
    $token = (Get-Content $tokenFile).Trim()
}

$targetSha = (Get-Content .last_commit_sha).Trim()
$url = "https://api.github.com/repos/$fullRepo/actions/runs?per_page=5"

# 构造请求头
$headers = @{
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "PowerShell-AI-Workflow"
}
if (-not [string]::IsNullOrEmpty($token)) {
    $headers.Add("Authorization", "Bearer $token")
}

Write-Host "⏳ 正在动态为您建立云端打包监控通道 (目标仓库: $fullRepo)..." -ForegroundColor Yellow
Write-Host "⏳ 等待提交的 GitHub Actions 运行记录启动..." -ForegroundColor Yellow

$maxRetries = 40 # 💡 设置最大轮询次数，每次间隔 10 秒，累计 6.6 分钟超时保护，防止卡死
$attempt = 0
$runFound = $false

while ($attempt -lt $maxRetries) {
    $attempt++
    try {
        $response = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
        
        # 寻找匹配当前 SHA 的 Actions 构建记录
        $run = $response.workflow_runs | Where-Object { $_.head_sha -eq $targetSha } | Select-Object -First 1
        
        if ($null -eq $run) {
            Write-Host "云端正在排队初始化中 ($attempt/$maxRetries)..."
            Start-Sleep -Seconds 10
            continue
        }
        
        $runFound = $true
        $runId = $run.id
        
        if ($run.status -eq "completed") {
            # 编译完成，清理本地临时 Commit SHA 文件
            if (Test-Path .last_commit_sha) { Remove-Item .last_commit_sha }
            
            if ($run.conclusion -eq "success") {
                Write-Host "`n=========================================" -ForegroundColor Green
                Write-Host " 🎉 恭喜！云端已全自动打包编译并签名成功！" -ForegroundColor Green
                Write-Host " 🚀 产物已顺利发布至 GitHub Release 附件中。" -ForegroundColor Green
                Write-Host " 🔗 产物详情与下载页面: $($run.html_url)" -ForegroundColor Green
                Write-Host "=========================================" -ForegroundColor Green
                break
            } else {
                Write-Host "`n❌ 警告！GitHub Actions 打包失败！编译结论为: $($run.conclusion)" -ForegroundColor Red
                Write-Host "🔍 正在为您启动 AI 编译故障自诊断系统，正在抓取错误日志..." -ForegroundColor Yellow
                
                # 寻找编译失败的具体 Job 列表
                $jobsUrl = "https://api.github.com/repos/$fullRepo/actions/runs/$runId/jobs"
                $jobsResponse = Invoke-RestMethod -Uri $jobsUrl -Headers $headers -Method Get
                $failedJob = $jobsResponse.jobs | Where-Object { $_.conclusion -eq "failure" } | Select-Object -First 1
                
                if ($null -ne $failedJob) {
                    Write-Host "定位到失败的 Job 名称: $($failedJob.name) (ID: $($failedJob.id))`n" -ForegroundColor Gray
                    $logUrl = "https://api.github.com/repos/$fullRepo/actions/jobs/$($failedJob.id)/logs"
                    
                    # 💡 安全获取失败日志：处理 redirect 重定向，并在重定向跳转时主动剥离 token，防 S3 400 鉴权失败
                    try {
                        $webResponse = Invoke-WebRequest -Uri $logUrl -Headers $headers -MaximumRedirection 0 -ErrorAction SilentlyContinue
                        $redirectUrl = $webResponse.Headers.Location
                        if ($null -ne $redirectUrl) {
                            $logs = Invoke-RestMethod -Uri $redirectUrl -Method Get
                        } else {
                            $logs = Invoke-RestMethod -Uri $logUrl -Headers $headers -Method Get
                        }
                    } catch {
                        # 匿名或备用 curl 方式拉取
                        if (-not [string]::IsNullOrEmpty($token)) {
                            $logs = curl.exe -L -s -H "Authorization: Bearer $token" $logUrl
                        } else {
                            $logs = curl.exe -L -s $logUrl
                        }
                    }

                    if ($logs) {
                        $logLines = $logs -split "`n"
                        Write-Host "--- 📄 截取 GitHub 云编译报错日志后 50 行 ---" -ForegroundColor Red
                        $logLines | Select-Object -Last 50
                        Write-Host "--------------------------------------------" -ForegroundColor Red
                    } else {
                        Write-Host "无法拉取到详细的编译文本日志。" -ForegroundColor Red
                    }
                }
                
                Write-Host "`n🔗 查看 GitHub 官方编译控制台详情: $($run.html_url)" -ForegroundColor Yellow
                exit 1
            }
        }
        
        Write-Host "云端正在进行编译 (当前状态: $($run.status))... 10秒后重新查询..."
        Start-Sleep -Seconds 10
        
    } catch {
        Write-Host "连接 GitHub API 时发生网络波动: $_. 5秒后重试..."
        Start-Sleep -Seconds 5
    }
}

if ($attempt -ge $maxRetries) {
    Write-Host "`n⏱️ 监控超时限制已达上限 ($maxRetries 次轮询)。已为您安全退出监控进程。" -ForegroundColor Yellow
    Write-Host "💡 提示: 云端构建可能仍在进行中。请稍后直接刷新您的 GitHub 页面进行查看。" -ForegroundColor Yellow
}
```
