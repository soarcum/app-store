---
description: commit@build
---

# 🤖 AI 驱动的 Android 应用全自动初始化与自愈打包工作流

本工作流为 AI 智能体（Agent）专用开发指南，定义了两个核心场景的标准化操作路径：**第一次一键初始化** 与 **增量更新 AI 自愈打包**。
通过这套机制，AI 智能体将以完全无人值守的方式完成 App 重命名、图标 AI 创作、云端打包，并对任何编译错误进行自我修复与迭代，直至 APK 成功发布。

---

## 🛠️ 核心场景 1：第一次运行（一键创建与定制应用）

当用户发出“创建一个新应用”、“将该模板初始化为 [应用名]”等指令时，AI 必须严格执行以下流程：

### 1. 自动生成高质感应用图标
AI 智能体应使用 `generate_image` 工具为应用生成一张无缝适配、极具现代设计感（如微光渐变、扁平化或精致微 3D 图案）的 512x512 PNG 图像。
- **Prompt 推荐**：`"A stunning flat high-quality Android app icon for [应用主题/名称], premium gradient background, sleek minimalist vector design, highly detailed, 512x512, no device frame"`
- **保存路径**：保存为本地临时文件或直接覆盖至 `app/src/main/res/drawable/ic_launcher_foreground.png`。

### 2. 调用重命名脚本完成一键定制
AI 智能体必须以**静默无人值守模式**在终端执行 `rename_project.ps1` 脚本，将包名、应用名、图标全局同步：
```powershell
# AI 根据应用主题和域名，动态指定 NewPackage（如 com.slowpack.appstore）与 NewAppName（如 应用商店）
# IconPath 为上一步生成的应用图标绝对路径
powershell.exe -ExecutionPolicy Bypass -File .\rename_project.ps1 -NewPackage "com.slowpack.appstore" -NewAppName "应用商店" -IconPath "C:\path\to\generated_icon.png"
```
脚本执行完毕后：
- `config.gradle` 的应用属性已更新。
- `strings.xml` 显示名称已完美修改，且 `settings.gradle` 项目名已同步。
- 物理包路径已全部迁移完成。
- 自适应图标配置已全部装配完成。

### 3. 一键首次提交并推送到 GitHub
执行以下命令：
```powershell
# 1. 提交初始定制后的代码
git add .
git commit -m "chore: initialize project as AppStore with AI-generated premium icon"
git tag -a "v1.0.0" -m "Initial version v1.0.0"

# 2. 推送到 GitHub（带 TLS 网络容错）
git -c http.sslVerify=false push origin main --tags
```

### 4. 建立云端打包监控通道（见步骤 3 轮询机制）
轮询监控 GitHub Actions 编译进度，直至首个初始 APK 成功挂载。

---

## 🔄 核心场景 2：增量修改与 AI 错误自愈编译

在项目初始化完毕后，每次 AI 智能体修改了代码、修复了 Bug 或应用户要求新增了业务功能时，必须启动增量打包和自愈流程：

### 1. 自动递增版本号并推送修改
AI 智能体直接调用本地的 `bump_and_push.ps1` 脚本。该脚本会自动计算自增版本号、修改 `config.gradle`、暂存所有代码并一键推送到 GitHub。
> 💡 **AI 智能体指令**：
> 运行该脚本必须带上 `-Notes` 参数（其内容为本次修改的简要总结），以激活“全自动无人值守模式”。
```powershell
# AI 智能体在此输入本次更新的简要说明，随后一键运行部署
$Notes = "AI 自动提交：优化了业务主界面布局与更新逻辑。"
powershell.exe -ExecutionPolicy Bypass -File .\bump_and_push.ps1 -Notes $Notes

# 提取最新的 Git 提交 SHA，准备开始轮询
$lastSha = (git rev-parse HEAD).Trim()
$lastSha | Set-Content .last_commit_sha -NoNewline
Write-Host "✅ 本地推送已完成，最新 Commit SHA 为: $lastSha"
```

### 2. 轮询并监听 Actions 打包状态（防 400 鉴权剥离）
AI 智能体将在后台轮询 GitHub Actions API，监听针对刚才推送的 SHA 的云端构建状态。
```powershell
$ErrorActionPreference = "Stop"

# 1. 动态解析仓库信息与 Token
$configContent = Get-Content "config.gradle" -Raw -Encoding UTF8
$owner = [regex]::Match($configContent, 'githubOwner\s*=\s*"([^"]+)"').Groups[1].Value.Trim()
$repo = [regex]::Match($configContent, 'githubRepo\s*=\s*"([^"]+)"').Groups[1].Value.Trim()
$fullRepo = "$owner/$repo"

$token = ""
if (Test-Path ".github_token") { $token = (Get-Content ".github_token").Trim() }

$targetSha = (Get-Content .last_commit_sha).Trim()
$url = "https://api.github.com/repos/$fullRepo/actions/runs?per_page=5"

$headers = @{
    "Accept" = "application/vnd.github.v3+json"
    "User-Agent" = "PowerShell-AI-Workflow"
}
if (-not [string]::IsNullOrEmpty($token)) { $headers.Add("Authorization", "Bearer $token") }

Write-Host "⏳ 正在动态为您建立云端打包监控通道 (仓库: $fullRepo)..." -ForegroundColor Yellow

$maxRetries = 40
$attempt = 0
while ($attempt -lt $maxRetries) {
    $attempt++
    try {
        $response = Invoke-RestMethod -Uri $url -Headers $headers -Method Get
        $run = $response.workflow_runs | Where-Object { $_.head_sha -eq $targetSha } | Select-Object -First 1
        
        if ($null -eq $run) {
            Write-Host "云端正在排队初始化中 ($attempt/$maxRetries)..."
            Start-Sleep -Seconds 10
            continue
        }
        
        $runId = $run.id
        if ($run.status -eq "completed") {
            if (Test-Path .last_commit_sha) { Remove-Item .last_commit_sha }
            
            if ($run.conclusion -eq "success") {
                Write-Host "`n=========================================" -ForegroundColor Green
                Write-Host " 🎉 恭喜！云端已全自动打包编译并签名成功！" -ForegroundColor Green
                Write-Host " 🚀 产物已顺利以英文无乱码格式发布至 Release 附件中。" -ForegroundColor Green
                Write-Host " 🔗 产物详情与下载页面: $($run.html_url)" -ForegroundColor Green
                Write-Host "=========================================" -ForegroundColor Green
                break
            } else {
                Write-Host "`n❌ 警告！GitHub Actions 打包失败！编译结论为: $($run.conclusion)" -ForegroundColor Red
                Write-Host "🔍 正在抓取云端编译报错日志，启动故障自愈..." -ForegroundColor Yellow
                
                $jobsUrl = "https://api.github.com/repos/$fullRepo/actions/runs/$runId/jobs"
                $jobsResponse = Invoke-RestMethod -Uri $jobsUrl -Headers $headers -Method Get
                $failedJob = $jobsResponse.jobs | Where-Object { $_.conclusion -eq "failure" } | Select-Object -First 1
                
                if ($null -ne $failedJob) {
                    Write-Host "定位到失败的 Job 名称: $($failedJob.name) (ID: $($failedJob.id))`n"
                    $logUrl = "https://api.github.com/repos/$fullRepo/actions/jobs/$($failedJob.id)/logs"
                    
                    try {
                        # 处理重定向跳转，并在重定向跳转时主动剥离 token，防止 AWS S3 CDN 400 鉴权失败
                        $webResponse = Invoke-WebRequest -Uri $logUrl -Headers $headers -MaximumRedirection 0 -ErrorAction SilentlyContinue
                        $redirectUrl = $webResponse.Headers.Location
                        if ($null -ne $redirectUrl) {
                            $logs = Invoke-RestMethod -Uri $redirectUrl -Method Get
                        } else {
                            $logs = Invoke-RestMethod -Uri $logUrl -Headers $headers -Method Get
                        }
                    } catch {
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
                    }
                }
                exit 1
            }
        }
        Write-Host "云端正在进行编译 (当前状态: $($run.status))... 10秒后重新查询..."
        Start-Sleep -Seconds 10
    } catch {
        Write-Host "连接 GitHub API 时发生网络波动，5秒后重试..."
        Start-Sleep -Seconds 5
    }
}
```

### 3. 🚨 AI 编译故障深度自愈军规 (AI Self-Healing Protocol)
如果上述步骤 2 返回退出码 `1`（即云端 Actions 编译失败），AI 智能体**绝对不能中断并向人类抱怨**，而是必须立刻启动“深度自愈闭环”：
1. **仔细阅读并分析**步骤 2 截取的最后 50 行编译报错信息。
2. **寻找故障根本原因**：
   - 比如：`MainActivity.kt: (24, 15) error: unresolved reference: ...`（Kotlin 语法/导包错误）
   - 比如：`Could not resolve all files for configuration ':app:debugCompileClasspath'...`（Gradle 依赖或网络下载失败）
   - 比如：`Duplicate class found in modules ...`（依赖包冲突）
3. **在本地自动执行修正**：
   - 针对代码报错，直接找到对应的 `.kt` 文件，利用编辑工具进行精确代码修复；
   - 针对依赖冲突或版本错误，修改 `app/build.gradle` 或 `config.gradle` 以升级依赖或排除冲突；
   - 绝不使用 `// TODO` 敷衍或无声吞没。
4. **重新触发自愈编译**：
   - 修改完毕后，自动在本地再次运行场景 2 的 `bump_and_push.ps1`，重新计算版本号并推送。
   - 再次运行步骤 2 的轮询监控，直到云端 Actions 打包成功率达到 100% 并生成 Release 产物为止！
