package com.slowpack.appstore.feature.store.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.slowpack.appstore.feature.store.model.AppDisplayItem
import com.slowpack.appstore.feature.store.model.AppInstallStatus
import com.slowpack.appstore.feature.store.service.StoreManager
import kotlinx.coroutines.launch

// 💡 下载进度状态
private data class DownloadProgress(
    val appId: String,
    val progress: Int = 0,
    val downloadedMB: Float = 0f,
    val totalMB: Float = 0f,
    val isInstalling: Boolean = false,
    val errorMsg: String? = null
)

/**
 * 💡 应用商店主界面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStoreScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<AppDisplayItem>>(emptyList()) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf<DownloadProgress?>(null) }

    // 💡 加载应用列表的函数
    fun loadApps() {
        scope.launch {
            isLoading = true
            loadError = null
            val items = StoreManager.loadDisplayItems(context)
            if (items.isEmpty()) {
                loadError = "暂未配置任何应用，请检查 data/apps.json"
            }
            apps = items
            isLoading = false
        }
    }

    // 首次加载
    LaunchedEffect(Unit) { loadApps() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "应用商店",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "数据驱动 · GitHub 私有仓库分发",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadApps() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    // 加载中
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "正在从 GitHub 私有仓库拉取应用列表...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                loadError != null && apps.isEmpty() -> {
                    // 加载失败
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "😥 $loadError",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { loadApps() }) {
                            Text("重试")
                        }
                    }
                }

                else -> {
                    // 应用列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = innerPadding
                    ) {
                        items(apps, key = { it.info.id }) { item ->
                            AppCard(
                                item = item,
                                isDownloading = downloadProgress?.appId == item.info.id,
                                downloadProgress = if (downloadProgress?.appId == item.info.id) downloadProgress else null,
                                onDownload = {
                                    val url = item.apkDownloadUrl
                                    val version = item.latestVersion
                                    if (url != null && version != null) {
                                        downloadProgress = DownloadProgress(appId = item.info.id)
                                        scope.launch {
                                            StoreManager.downloadAndInstallApk(
                                                context = context,
                                                downloadUrl = url,
                                                appName = item.info.id,
                                                version = version,
                                                onProgress = { p, dl, total ->
                                                    downloadProgress = DownloadProgress(
                                                        appId = item.info.id,
                                                        progress = p,
                                                        downloadedMB = dl,
                                                        totalMB = total
                                                    )
                                                },
                                                onInstalling = {
                                                    downloadProgress = DownloadProgress(
                                                        appId = item.info.id,
                                                        isInstalling = true
                                                    )
                                                },
                                                onError = { msg ->
                                                    downloadProgress = DownloadProgress(
                                                        appId = item.info.id,
                                                        errorMsg = msg
                                                    )
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                }
                                            )
                                        }
                                    }
                                },
                                onOpen = {
                                    StoreManager.launchApp(context, item.info.packageName)
                                }
                            )
                        }

                        // 底部留白
                        item { Spacer(modifier = Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

/**
 * 💡 单个应用卡片组件
 */
@Composable
private fun AppCard(
    item: AppDisplayItem,
    isDownloading: Boolean,
    downloadProgress: DownloadProgress?,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = try {
        Color(android.graphics.Color.parseColor(item.info.accentColor))
    } catch (e: Exception) {
        MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 💡 渐变色首字母头像
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.info.iconLetter,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.info.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.info.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }

                // 💡 版本号 Badge
                if (item.latestVersion != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "v${item.latestVersion}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 描述
            Text(
                text = item.info.description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            // 更新日志
            if (item.releaseNotes.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "📋 ${item.releaseNotes.take(120)}${if (item.releaseNotes.length > 120) "..." else ""}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 💡 下载进度条（仅在下载中显示）
            AnimatedVisibility(
                visible = isDownloading && downloadProgress != null && !downloadProgress.isInstalling && downloadProgress.errorMsg == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (downloadProgress != null) {
                    Column {
                        val progressFloat = if (downloadProgress.progress >= 0) downloadProgress.progress / 100f else 0f
                        LinearProgressIndicator(
                            progress = progressFloat,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accentColor
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${downloadProgress.progress}%",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = accentColor
                            )
                            if (downloadProgress.totalMB > 0) {
                                Text(
                                    text = String.format("%.1f / %.1f MB", downloadProgress.downloadedMB, downloadProgress.totalMB),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // 💡 状态操作按钮
            val buttonColors: Pair<Color, String>
            val buttonIcon: @Composable () -> Unit

            when {
                isDownloading && downloadProgress?.isInstalling == true -> {
                    buttonColors = Pair(accentColor.copy(alpha = 0.6f), "正在安装...")
                    buttonIcon = { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) }
                }
                isDownloading && downloadProgress?.errorMsg == null -> {
                    buttonColors = Pair(accentColor.copy(alpha = 0.6f), "下载中...")
                    buttonIcon = { CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp) }
                }
                item.status is AppInstallStatus.Updatable -> {
                    buttonColors = Pair(Color(0xFFFF6D00), "更新至 v${(item.status as AppInstallStatus.Updatable).latestVersion}")
                    buttonIcon = { Icon(Icons.Default.SystemUpdateAlt, contentDescription = "Update", modifier = Modifier.size(16.dp)) }
                }
                item.status is AppInstallStatus.Installed -> {
                    buttonColors = Pair(Color(0xFF2E7D32), "打开应用")
                    buttonIcon = { Icon(Icons.Default.OpenInNew, contentDescription = "Open", modifier = Modifier.size(16.dp)) }
                }
                else -> {
                    buttonColors = Pair(accentColor, "立即下载")
                    buttonIcon = { Icon(Icons.Default.CloudDownload, contentDescription = "Download", modifier = Modifier.size(16.dp)) }
                }
            }

            Button(
                onClick = {
                    when {
                        isDownloading -> {} // 下载中，禁止操作
                        item.status is AppInstallStatus.Installed && item.status !is AppInstallStatus.Updatable -> onOpen()
                        else -> onDownload()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = buttonColors.first),
                enabled = !isDownloading
            ) {
                buttonIcon()
                Spacer(modifier = Modifier.width(6.dp))
                Text(buttonColors.second, fontSize = 13.sp)
            }

            // 已安装版本提示
            if (item.status is AppInstallStatus.Installed) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "已安装 v${(item.status as AppInstallStatus.Installed).installedVersion}",
                    fontSize = 10.sp,
                    color = Color(0xFF4CAF50),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}
