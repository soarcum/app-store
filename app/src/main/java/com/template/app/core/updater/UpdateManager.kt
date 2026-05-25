package com.template.app.core.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.template.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 💡 升级信息数据实体
 */
data class UpdateInfo(
    val version: String,
    val downloadUrl: String,
    val body: String
)

/**
 * 💡 升级下载进度状态密封类
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val progress: Int, val downloadedBytes: Long, val totalBytes: Long) : DownloadState()
    data class Installing(val file: File) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

/**
 * 💡 高可靠自动更新系统 (支持公开/私有 GitHub 仓库自适应更新)
 */
object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val MAX_RETRY = 3

    // 💡 动态拼装 GitHub Releases 最新发布 API，数据来自 build.gradle 注入的 BuildConfig
    private val GITHUB_API = "https://api.github.com/repos/${BuildConfig.GITHUB_OWNER}/${BuildConfig.GITHUB_REPO}/releases/latest"

    // 💡 可选的私有仓库 Token。如果是公开仓库，可以保持为空字符串 ""
    private const val GITHUB_TOKEN = ""

    // 基础 HTTP 请求客户端 (支持长连接超时)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // 💡 专用于下载 APK 文件的客户端。必须关闭自动重定向，以便手动处理 302 授权头部剥离
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /**
     * 获取应用当前版本号 versionName
     */
    fun getCurrentVersion(context: Context): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "0.0.0"
        }
    }

    /**
     * 检测 GitHub 是否有新版本发布
     */
    suspend fun checkForUpdate(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val reqBuilder = Request.Builder()
                .url(GITHUB_API)
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("User-Agent", "AndroidTemplateUpdater")
            
            // 💡 如果配置了 Token 则携带，支持私有库；否则以匿名公开模式调用 (每小时上限60次)
            if (GITHUB_TOKEN.isNotEmpty()) {
                reqBuilder.addHeader("Authorization", "Bearer $GITHUB_TOKEN")
            }

            val request = reqBuilder.build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "检测更新失败，HTTP 状态码: ${response.code}")
                    return@withContext null
                }

                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tagName = json.getString("tag_name").removePrefix("v")
                val releaseBody = json.optString("body", "")

                val currentVersion = getCurrentVersion(context)
                Log.i(TAG, "本地版本: $currentVersion, 云端最新版本: $tagName")

                if (isNewerVersion(tagName, currentVersion)) {
                    val assets = json.getJSONArray("assets")
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        // 寻找构建产物中的 APK 文件
                        if (name.endsWith(".apk")) {
                            return@withContext UpdateInfo(
                                version = tagName,
                                downloadUrl = asset.getString("url"),
                                body = releaseBody
                            )
                        }
                    }
                    Log.w(TAG, "云端 Release 列表中未找到任何 APK 格式的文件")
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "检测新版本发生异常: ${e.message}")
            null
        }
    }

    /**
     * 对比版本号大小 (支持多位数，如 1.0.12 > 1.0.9)
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
     * 下载并启动安装 APK 的主干逻辑 (包含重试和缓存逻辑)
     */
    suspend fun downloadAndInstall(
        context: Context,
        downloadUrl: String,
        version: String,
        onStateChange: (DownloadState) -> Unit
    ) = withContext(Dispatchers.IO) {
        val fileName = "${BuildConfig.GITHUB_REPO}-v$version.apk"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)

        // 💡 命中本地缓存：如果发现相同版本的安装包已下载且完好，免去重复下载，直接调起安装
        if (file.exists() && file.length() > 1024 * 1024) {
            Log.i(TAG, "发现已缓存的最新安装包: ${file.name}")
            onStateChange(DownloadState.Installing(file))
            installApk(context, file)
            return@withContext
        }

        var lastError: String? = null
        for (attempt in 1..MAX_RETRY) {
            Log.i(TAG, "准备开始下载新版本，尝试次数 $attempt/$MAX_RETRY")
            onStateChange(DownloadState.Downloading(0, 0, 0))
            try {
                downloadApk(downloadUrl, file, onStateChange)
                Log.i(TAG, "下载成功，总大小: ${file.length()} 字节")
                onStateChange(DownloadState.Installing(file))
                installApk(context, file)
                return@withContext
            } catch (e: Exception) {
                lastError = e.message ?: "未知网络错误"
                Log.e(TAG, "第 $attempt 次下载尝试失败: $lastError")
                if (file.exists()) file.delete()
                if (attempt < MAX_RETRY) {
                    onStateChange(DownloadState.Downloading(0, 0, 0)) // 重置状态进行下一次重试
                }
            }
        }

        val errorMsg = "重试 $MAX_RETRY 次后依然下载失败，原因为: $lastError"
        Log.e(TAG, errorMsg)
        onStateChange(DownloadState.Error(errorMsg))
    }

    /**
     * 核心安全网络流下载 (处理 302 鉴权头部剥离)
     */
    private fun downloadApk(
        downloadUrl: String,
        targetFile: File,
        onStateChange: (DownloadState) -> Unit
    ) {
        val reqBuilder = Request.Builder()
            .url(downloadUrl)
            .addHeader("Accept", "application/octet-stream")
            .addHeader("User-Agent", "AndroidTemplateUpdater")
        
        if (GITHUB_TOKEN.isNotEmpty()) {
            reqBuilder.addHeader("Authorization", "Bearer $GITHUB_TOKEN")
        }

        val authedRequest = reqBuilder.build()

        downloadClient.newCall(authedRequest).execute().use { firstResp ->
            val streamResp = when (firstResp.code) {
                301, 302, 303, 307, 308 -> {
                    val location = firstResp.header("Location")
                        ?: throw RuntimeException("收到重定向指令 ${firstResp.code}，但找不到 Location 响应头")
                    
                    // 💡 关键安全设计：GitHub Release API 重定向到 AWS S3 真实 CDN 存储时，
                    // 必须彻底剥离 Authorization 请求头，否则会触发 AWS S3 安全拒绝 (400 Bad Request)
                    val cdnRequest = Request.Builder()
                        .url(location)
                        .addHeader("User-Agent", "AndroidTemplateUpdater")
                        .build()
                    downloadClient.newCall(cdnRequest).execute()
                }
                200 -> firstResp
                else -> throw RuntimeException("HTTP 响应错误: ${firstResp.code}")
            }

            val ownsResp = streamResp !== firstResp
            try {
                if (!streamResp.isSuccessful) {
                    throw RuntimeException("从 CDN 下载失败，HTTP 状态码: ${streamResp.code}")
                }
                writeBodyToFile(streamResp, targetFile, onStateChange)
            } finally {
                if (ownsResp) streamResp.close()
            }
        }
    }

    /**
     * 将下载的二进制数据流平滑地写入临时文件，并实时统计进度反馈给 UI 层
     */
    private fun writeBodyToFile(
        response: okhttp3.Response,
        targetFile: File,
        onStateChange: (DownloadState) -> Unit
    ) {
        val body = response.body ?: throw RuntimeException("下载内容响应体为空")
        val totalBytes = body.contentLength()
        val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")

        try {
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var downloadedBytes = 0L
                    var lastProgressReport = 0

                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progress = if (totalBytes > 0) {
                            (downloadedBytes * 100 / totalBytes).toInt()
                        } else {
                            -1 // 应对某些服务器不返回 Content-Length 的未知大小情况
                        }

                        // 💡 只有进度数值发生改变时才分发 UI 回调，大幅减少 Compose UI 重绘开销
                        if (progress != lastProgressReport) {
                            lastProgressReport = progress
                            onStateChange(DownloadState.Downloading(progress, downloadedBytes, totalBytes))
                        }
                    }
                    output.flush()
                }
            }

            // 下载成功，重命名临时文件为正式安装包
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    /**
     * 调用 PackageInstaller 隐式安装
     */
    private fun installApk(context: Context, file: File) {
        Log.i(TAG, "准备调起系统包安装器安装: ${file.name}")
        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // 💡 在 Android 7.0+ 系统中，利用 FileProvider 生成安全 content:// 共享 URI
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            Uri.fromFile(file)
        }

        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        context.startActivity(intent)
    }
}
