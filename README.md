# 🚀 Android Jetpack Compose 极速开发种子模板

这是一个面向 Android 真机研发效能的**超级空模板 (Boilerplate)**。基于 **Kotlin + Jetpack Compose + Material 3** 现代原生技术栈构建，专注于帮您彻底剥离“非业务功能”的开发负担。

适用于**“Fork / 使用模板 -> 专注于业务 -> 一键自增发布 -> 手机自动推送升级”**的现代极速迭代流。

---

## ✨ 核心亮点功能

### 1. ⚙️ 全局唯一配置中心 (`config.gradle`)
彻底告别繁琐复杂的 Android 构建配置。在项目根目录集中定义所有的核心参数。
当您开发全新 App 时，**仅需在此修改四行代码**，应用包名、应用名称、自动更新所指向的仓库便全部自动同步生效，真正实现 DRY (Don't Repeat Yourself) 架构：
```groovy
ext {
    applicationId = "com.template.app" // 💡 新 App 包名
    appName = "AndroidBoilerplate"      // 💡 新 App 显示名称
    githubOwner = "your_github_username"// 💡 您的 GitHub 账号名
    githubRepo = "android-boilerplate"  // 💡 您的 GitHub 仓库名
}
```

### 2. 🔄 高可靠 GitHub 自动更新系统 (`UpdateManager.kt`)
内置功能完善的在线升级机制。支持**公开**与**私有** GitHub 仓库的双模式自适应更新：
* **零依赖网络拉取**：基于 OkHttp，完美处理了 GitHub API 重定向至 AWS S3 CDN 过程中的 302 鉴权头部自动剥除（防止触发 AWS 400 Bad Request 拒绝服务错误）。
* **通知进度监控**：后台下载并提供平滑的下载进度浮层显示，避免阻塞用户操作。
* **低权限申请**：将下载文件存储于外部私有沙盒，无需污染敏感的系统级存储读写权限申请。

### 3. 🛡️ 手机端崩溃可视化自诊断系统 (`GlobalExceptionHandler.kt` + `CrashActivity.kt`)
针对**“只有安卓手机，无电脑连接 ADB 调试”**场景定制的超级调试利器！
* **致命闪退阻断**：接管应用未捕获异常。发生崩溃时不闪退、不黑屏，而是优雅拦截并拉起在**独立进程 `:crash`**中运行的 `CrashActivity`，保证显示极其稳定。
* **高颜值排错面板**：以暖调代码风格高亮展示异常堆栈，附带完整的设备硬件诊断元数据。
* **一键辅助调试**：内置“一键复制日志”、“一键调用系统分享（发送至微信/钉钉/QQ）”和“一键重启 App”按钮，让您在床上拿着手机就能瞬间排查 Bug！

### 4. 🪄 双重智能重构重命名机制
* **云端 (Use this template)**：配置了 GitHub Actions `rename-template.yml` 工作流。当您在 GitHub 上点击“Use this template”新建项目后，云端将在 30 秒内**自动识别新仓库名**，自动重构所有物理 Kotlin 包目录结构、批量重写引用并提交。克隆下来即可直接写业务！
* **本地 (PowerShell 交互)**：内置 `rename_project.ps1` 脚本。如果您在本地手工克隆或 Fork，只需双击或终端运行该脚本，输入新包名和应用名，即可在**秒级完成**全项目物理目录重构、引用改写并清空编译缓存。

### 5. 📈 一键增版部署发布工作流 (`bump_and_push.ps1`)
在本地终端中运行此脚本：
1. 自动读取并计算当前版本：`versionCode` 自增 +1，`versionName` 的最后一位小版本自增 +1（如 `1.0.5` -> `1.0.6`）。
2. 引导输入本次更新的发布日志说明。
3. 自动将修改写入 `config.gradle`。
4. 自动暂存、打上带日志说明的 Git Tags 标签、自动执行 Commit 并强力推送至 GitHub。
5. 云端 Actions 接管，全自动进行 Debug 签名打包并创建 Release。
6. **2分钟后，您手中的手机自动弹出横幅：“检测到全新版本，是否更新？”**。

---

## 🚀 极速上手使用指南

### 第一步：Fork 或 Clone 项目
```bash
git clone https://github.com/your_github_username/android-template.git
```

### 第二步：初始化包名与应用名称
1. **方案 A (推荐)：** 直接在本地根目录下，使用 PowerShell 运行 `./rename_project.ps1`，根据命令行提示输入包名和应用名，一键完成初始化。
2. **方案 B (Android Studio 辅助)：** 直接用 Android Studio 打开项目，IDE 会自动识别并为您下载和生成本地的 `gradlew` 构建脚本。

### 第三步：专注于业务编写
所有的公共底层逻辑与框架已在 `core/` 下封装妥当。您以后在开发新 App 时，**仅需在 `com.yourname.app.features` 包下编写您的业务 Compose UI 与逻辑即可！**

### 第四步：一键版本发布与自动更新
当您在本地完成阶段性业务代码编写后，在根目录直接运行：
```powershell
./bump_and_push.ps1
```
输入本次的更新内容并按下回车。接下来，闭上双眼，等待云端将最新应用自动推送到您的手机上！
