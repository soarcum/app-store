package app.blankapp.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.blankapp.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自更新状态封装
 */
sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class NewVersionAvailable(
        val versionName: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val fileSize: Long
    ) : UpdateState
    data class Downloading(
        val progress: Int,
        val totalBytes: Long,
        val downloadedBytes: Long
    ) : UpdateState
    data class ReadyToInstall(
        val apkUri: Uri,
        val apkFile: File
    ) : UpdateState
    object NoUpdate : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * App 自更新引擎核心管理器 (UpdateManager)
 * 纯原生驱动，无需导入三方臃肿网络或JSON框架
 */
object UpdateManager {

    /**
     * 响应式状态，Compose UI 可以直接订阅并感应变化
     */
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    /**
     * 是否正在显示弹窗（供UI状态控制）
     */
    var showDialog by mutableStateOf(false)

    /**
     * 手动重设更新状态
     */
    fun resetState() {
        updateState = UpdateState.Idle
        showDialog = false
    }

    /**
     * 执行版本检测
     */
    fun checkUpdate() {
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO

        // 如果模板中的配置仍为占位符，说明尚未与GitHub仓库关联，直接跳过检测
        if (owner == "your-github-username" || repo == "your-repo-name" || owner.isBlank() || repo.isBlank()) {
            updateState = UpdateState.Idle
            return
        }

        updateState = UpdateState.Checking
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                // GitHub API 必须包含有效的 User-Agent 头，否则会被拒绝并返回 403 Forbidden
                connection.setRequestProperty("User-Agent", "AppUpdateEngine/1.0 (Android)")

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(responseText)
                    val tagName = json.getString("tag_name")
                    val body = json.optString("body", "暂无更新说明。")
                    val assets = json.getJSONArray("assets")

                    var apkUrl = ""
                    var apkSize = 0L
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            apkUrl = asset.getString("browser_download_url")
                            apkSize = asset.optLong("size", 0L)
                            break
                        }
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    if (isNewerVersion(currentVersion, tagName) && apkUrl.isNotEmpty()) {
                        updateState = UpdateState.NewVersionAvailable(
                            versionName = tagName,
                            releaseNotes = body,
                            downloadUrl = apkUrl,
                            fileSize = apkSize
                        )
                        showDialog = true
                    } else {
                        updateState = UpdateState.NoUpdate
                    }
                } else {
                    updateState = UpdateState.Error("检测失败，状态码: $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateState = UpdateState.Error("检测更新时网络异常: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 下载 APK 的核心流式引擎
     * 自动处理 3xx 复杂的重定向逻辑，并默认使用 ghproxy 代理加速下载
     */
    fun downloadApk(context: Context, downloadUrl: String) {
        if (updateState is UpdateState.Downloading) return

        // 默认内置 ghproxy 极速代理下载服务以应对国内 GitHub 直连困难的问题
        val finalUrl = if (downloadUrl.startsWith("https://github.com")) {
            "https://ghproxy.net/$downloadUrl"
        } else {
            downloadUrl
        }

        updateState = UpdateState.Downloading(0, 0L, 0L)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = URL(finalUrl)
                var connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.useCaches = false
                connection.instanceFollowRedirects = true

                var responseCode = connection.responseCode
                var redirectCount = 0
                // 手动且深度处理复杂的 HTTP 3xx 系列重定向地址
                while ((responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                            responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                            responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                            responseCode == 307 || responseCode == 308) && redirectCount < 5
                ) {
                    val newUrl = connection.getHeaderField("Location") ?: break
                    connection = URL(newUrl).openConnection() as HttpURLConnection
                    connection.connectTimeout = 15000
                    connection.readTimeout = 15000
                    connection.useCaches = false
                    connection.instanceFollowRedirects = true
                    responseCode = connection.responseCode
                    redirectCount++
                }

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val contentLength = connection.contentLengthLong
                    val inputStream = connection.inputStream

                    val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    if (dir != null && !dir.exists()) {
                        dir.mkdirs()
                    }
                    val apkFile = File(dir, "update.apk")
                    if (apkFile.exists()) {
                        apkFile.delete()
                    }

                    val outputStream = FileOutputStream(apkFile)
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    var totalBytesRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead

                        val progress = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt()
                        } else {
                            0
                        }
                        updateState = UpdateState.Downloading(progress, contentLength, totalBytesRead)
                    }

                    outputStream.flush()
                    outputStream.close()
                    inputStream.close()

                    // 构建基于 FileProvider 的安全共享 Content URI 并派发至 ReadyToInstall 状态
                    val apkUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    updateState = UpdateState.ReadyToInstall(apkUri, apkFile)
                } else {
                    updateState = UpdateState.Error("下载文件失败，HTTP 状态码: $responseCode")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                updateState = UpdateState.Error("下载已中断: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 唤起系统安装器安装 APK
     */
    fun installApk(context: Context, apkUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            updateState = UpdateState.Error("安装引导失败: ${e.localizedMessage}")
        }
    }

    /**
     * 严谨的语义化版本号（SemVer）比对算法
     * 彻底避免了单纯字符串对比出现的漏洞
     */
    private fun isNewerVersion(current: String, latest: String): Boolean {
        val curClean = current.removePrefix("v").trim()
        val latClean = latest.removePrefix("v").trim()
        if (curClean == latClean) return false

        val curParts = curClean.split(".")
        val latParts = latClean.split(".")
        val length = maxOf(curParts.size, latParts.size)

        for (i in 0 until length) {
            val curNum = curParts.getOrNull(i)?.toIntOrNull() ?: 0
            val latNum = latParts.getOrNull(i)?.toIntOrNull() ?: 0
            if (latNum > curNum) return true
            if (latNum < curNum) return false
        }
        return false
    }
}
