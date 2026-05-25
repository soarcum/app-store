package com.slowpack.appstore.feature.store.model

/**
 * 💡 商店应用配置实体数据模型
 * 与 data/apps.json 中的每一项一一对应
 */
data class AppInfo(
    val id: String,
    val name: String,
    val packageName: String,
    val githubOwner: String,
    val githubRepo: String,
    val description: String,
    val iconLetter: String,
    val accentColor: String
)

/**
 * 💡 应用在当前设备上的安装状态
 */
sealed class AppInstallStatus {
    /** 未安装 */
    data object NotInstalled : AppInstallStatus()

    /** 已安装，附带已安装的版本号 */
    data class Installed(val installedVersion: String) : AppInstallStatus()

    /** 已安装但有可用更新 */
    data class Updatable(
        val installedVersion: String,
        val latestVersion: String
    ) : AppInstallStatus()
}

/**
 * 💡 商店列表中每个应用卡片的综合渲染状态
 */
data class AppDisplayItem(
    val info: AppInfo,
    val status: AppInstallStatus = AppInstallStatus.NotInstalled,
    val latestVersion: String? = null,
    val apkDownloadUrl: String? = null,
    val releaseNotes: String = ""
)
