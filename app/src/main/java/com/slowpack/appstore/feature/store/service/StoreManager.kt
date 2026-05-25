package com.slowpack.appstore.feature.store.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.slowpack.appstore.BuildConfig
import com.slowpack.appstore.feature.store.model.AppDisplayItem
import com.slowpack.appstore.feature.store.model.AppInfo
import com.slowpack.appstore.feature.store.model.AppInstallStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 💡 商店核心业务引擎
 * 负责拉取 apps.json、解析 Release、检测安装状态、下载并安装 APK
 */
object StoreManager {

    private const val TAG = "StoreManager"
    private const val MAX_RETRY = 3

    // 💡 优先通过 GitHub Contents API 拉取私有仓库中的 apps.json 文件（支持 v3.raw 头部直接返回文本，比 raw.githubusercontent.com 稳定且防封锁）
    private val APPS_JSON_URL =
        "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/contents/data/apps.json"

    private val GITHUB_TOKEN = BuildConfig.GITHUB_TOKEN

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 💡 用于 APK 下载，关闭自动重定向以手动剥离 Authorization 头部
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // ─── 数据拉取层 ───

    /**
     * 💡 从私有仓库拉取 apps.json 并解析为 AppInfo 列表（不再吞没错误，允许网络与鉴权异常抛出到 UI 层）
     */
    suspend fun fetchAppsList(): List<AppInfo> = withContext(Dispatchers.IO) {
        val request = buildAuthedRequest(APPS_JSON_URL)
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "拉取 apps.json 失败，HTTP 状态码: ${response.code}")
                throw RuntimeException("GitHub 响应失败 (HTTP ${response.code})")
            }
            val body = response.body?.string() ?: throw RuntimeException("响应体为空")
            parseAppsList(body)
        }
    }

    /**
     * 💡 解析 JSON 数组为 AppInfo 列表
     */
    private fun parseAppsList(jsonStr: String): List<AppInfo> {
        val result = mutableListOf<AppInfo>()
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                AppInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    packageName = obj.getString("packageName"),
                    githubOwner = obj.getString("githubOwner"),
                    githubRepo = obj.getString("githubRepo"),
                    description = obj.optString("description", ""),
                    iconLetter = obj.optString("iconLetter", "?"),
                    accentColor = obj.optString("accentColor", "#6200EE")
                )
            )
        }
        return result
    }

    // ─── 安装状态检测层 ───

    /**
     * 💡 检测某个包名的应用在当前设备的安装状态
     */
    fun checkInstallStatus(context: Context, packageName: String): AppInstallStatus {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(packageName, 0)
            val installedVersion = pkgInfo.versionName ?: "0.0.0"
            AppInstallStatus.Installed(installedVersion)
        } catch (e: PackageManager.NameNotFoundException) {
            AppInstallStatus.NotInstalled
        }
    }

    // ─── Release 解析层 ───

    /**
     * 💡 获取指定仓库的最新 Release 信息（版本号、APK 下载 URL、更新日志）
     */
    suspend fun fetchLatestRelease(
        owner: String,
        repo: String
    ): Triple<String, String, String>? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val request = buildAuthedRequest(url, accept = "application/vnd.github.v3+json")
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "获取 $owner/$repo Release 失败，HTTP: ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val version = json.getString("tag_name").removePrefix("v")
                val notes = json.optString("body", "")
                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        return@withContext Triple(version, asset.getString("url"), notes)
                    }
                }
                Log.w(TAG, "$owner/$repo Release 中未找到 APK 文件")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 $owner/$repo Release 异常: ${e.message}")
            null
        }
    }

    /**
     * 💡 综合加载：拉取应用列表 + 各应用的最新 Release + 本地安装状态
     */
    suspend fun loadDisplayItems(context: Context): List<AppDisplayItem> {
        val apps = fetchAppsList()
        return apps.map { app ->
            val localStatus = checkInstallStatus(context, app.packageName)
            val release = fetchLatestRelease(app.githubOwner, app.githubRepo)

            if (release != null) {
                val (latestVersion, apkUrl, notes) = release
                val finalStatus = when (localStatus) {
                    is AppInstallStatus.Installed -> {
                        if (isNewerVersion(latestVersion, localStatus.installedVersion)) {
                            AppInstallStatus.Updatable(localStatus.installedVersion, latestVersion)
                        } else {
                            localStatus
                        }
                    }
                    else -> localStatus
                }
                AppDisplayItem(
                    info = app,
                    status = finalStatus,
                    latestVersion = latestVersion,
                    apkDownloadUrl = apkUrl,
                    releaseNotes = notes
                )
            } else {
                AppDisplayItem(info = app, status = localStatus)
            }
        }
    }

    // ─── 下载安装层 ───

    /**
     * 💡 下载 APK 并调起安装
     */
    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        appName: String,
        version: String,
        onProgress: (progress: Int, downloadedMB: Float, totalMB: Float) -> Unit,
        onInstalling: () -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val fileName = "$appName-v$version.apk"
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )

        // 命中本地缓存
        if (file.exists() && file.length() > 1024 * 1024) {
            Log.i(TAG, "命中缓存: ${file.name}")
            onInstalling()
            installApk(context, file)
            return@withContext
        }

        var lastError: String? = null
        for (attempt in 1..MAX_RETRY) {
            try {
                downloadApkFile(downloadUrl, file, onProgress)
                onInstalling()
                installApk(context, file)
                return@withContext
            } catch (e: Exception) {
                lastError = e.message ?: "未知错误"
                Log.e(TAG, "下载尝试 $attempt/$MAX_RETRY 失败: $lastError")
                if (file.exists()) file.delete()
            }
        }
        onError("重试 $MAX_RETRY 次后依然失败: $lastError")
    }

    /**
     * 💡 核心下载实现（处理 GitHub 302 重定向时剥离 Authorization 避免 AWS S3 400）
     */
    private fun downloadApkFile(
        url: String,
        targetFile: File,
        onProgress: (Int, Float, Float) -> Unit
    ) {
        val request = buildAuthedRequest(url, accept = "application/octet-stream")
        downloadClient.newCall(request).execute().use { firstResp ->
            val streamResp = when (firstResp.code) {
                301, 302, 303, 307, 308 -> {
                    val location = firstResp.header("Location")
                        ?: throw RuntimeException("重定向 ${firstResp.code} 缺少 Location 头")
                    // 💡 关键：重定向到 AWS S3 时必须剥离 Authorization
                    val cdnReq = Request.Builder()
                        .url(location)
                        .addHeader("User-Agent", "AppStoreDownloader")
                        .build()
                    downloadClient.newCall(cdnReq).execute()
                }
                200 -> firstResp
                else -> throw RuntimeException("HTTP 错误: ${firstResp.code}")
            }

            val ownsResp = streamResp !== firstResp
            try {
                if (!streamResp.isSuccessful) {
                    throw RuntimeException("CDN 下载失败: ${streamResp.code}")
                }
                val body = streamResp.body ?: throw RuntimeException("响应体为空")
                val totalBytes = body.contentLength()
                val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

                try {
                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            var lastProgress = -1
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                val progress = if (totalBytes > 0) (downloaded * 100 / totalBytes).toInt() else -1
                                if (progress != lastProgress) {
                                    lastProgress = progress
                                    val dlMB = downloaded / (1024f * 1024f)
                                    val totalMB = totalBytes / (1024f * 1024f)
                                    onProgress(progress, dlMB, totalMB)
                                }
                            }
                            output.flush()
                        }
                    }
                    if (!tempFile.renameTo(targetFile)) {
                        tempFile.copyTo(targetFile, overwrite = true)
                        tempFile.delete()
                    }
                } catch (e: Exception) {
                    tempFile.delete()
                    throw e
                }
            } finally {
                if (ownsResp) streamResp.close()
            }
        }
    }

    // ─── 工具函数 ───

    /**
     * 💡 调起系统安装程序
     */
    private fun installApk(context: Context, file: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }
            setDataAndType(uri, "application/vnd.android.package-archive")
        }
        context.startActivity(intent)
    }

    /**
     * 💡 拉起已安装的应用
     */
    fun launchApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * 💡 版本号比较
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(l.size, c.size)
        for (i in 0 until maxLen) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    /**
     * 💡 构建携带 Token 的授权请求
     */
    private fun buildAuthedRequest(
        url: String,
        accept: String = "application/vnd.github.v3.raw"
    ): Request {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Accept", accept)
            .addHeader("User-Agent", "AppStoreClient")
        if (GITHUB_TOKEN.isNotEmpty()) {
            builder.addHeader("Authorization", "Bearer $GITHUB_TOKEN")
        }
        return builder.build()
    }
}
