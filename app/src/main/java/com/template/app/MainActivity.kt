package com.template.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.template.app.core.crash.GlobalExceptionHandler
import com.template.app.core.theme.AppTheme
import com.template.app.core.updater.DownloadState
import com.template.app.core.updater.UpdateInfo
import com.template.app.core.updater.UpdateManager
import kotlinx.coroutines.launch

/**
 * 💡 应用程序主入口 MainActivity
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 💡 注册全局崩溃拦截器，阻断闪退，将异常接管并交由 CrashActivity 稳定展示
        GlobalExceptionHandler.register(this)

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 💡 页面交互状态机
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var hasChecked by remember { mutableStateOf(false) }
    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }

    val currentVersion = remember { UpdateManager.getCurrentVersion(context) }

    // 💡 首次进入页面时自动静默检查更新
    LaunchedEffect(Unit) {
        isCheckingUpdate = true
        val info = UpdateManager.checkForUpdate(context)
        updateInfo = info
        isCheckingUpdate = false
        hasChecked = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Android 快速开发骨架",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 核心应用信息卡片
                AppInfoCard(currentVersion = currentVersion)

                Spacer(modifier = Modifier.height(20.dp))

                // 2. 更新状态面板 (有新版本时显示，无新版本时显示已是最新)
                UpdateStatusPanel(
                    isChecking = isCheckingUpdate,
                    hasChecked = hasChecked,
                    updateInfo = updateInfo,
                    downloadState = downloadState,
                    onStartDownload = { info ->
                        coroutineScope.launch {
                            UpdateManager.downloadAndInstall(
                                context = context,
                                downloadUrl = info.downloadUrl,
                                version = info.version,
                                onStateChange = { state ->
                                    downloadState = state
                                }
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                // 3. 开发者调试利器测试模块
                DeveloperDebugCard()
            }

            // 4. 全局浮动的下载进度蒙层 (在下载中时展现磨砂遮罩体验)
            DownloadProgressOverlay(downloadState = downloadState)
        }
    }
}

@Composable
fun AppInfoCard(currentVersion: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "✨ 欢迎使用全新模板",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "此模板已集成 Material 3 动态色盘、高可靠自动更新、以及本地崩溃自诊断系统。这让您未来的每一个 App 开发都可以做到直接克隆，无需再为架构琐事烦恼。",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Version",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "当前应用版本: v$currentVersion",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun UpdateStatusPanel(
    isChecking: Boolean,
    hasChecked: Boolean,
    updateInfo: UpdateInfo?,
    downloadState: DownloadState,
    onStartDownload: (UpdateInfo) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isChecking) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(16.dp)
            ) {
                CircularProgressIndicator(size = 18.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("正在从 GitHub 检索最新版本...", fontSize = 14.sp)
            }
        } else if (hasChecked) {
            if (updateInfo != null) {
                // 发现新版本！展示引人注目的升级卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.SystemUpdate,
                                contentDescription = "New version",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "🎉 发现全新版本 v${updateInfo.version} !",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "更新日志:\n${updateInfo.body.ifBlank { "开发者很懒，未编写日志~" }}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (downloadState is DownloadState.Idle || downloadState is DownloadState.Error) {
                            Button(
                                onClick = { onStartDownload(updateInfo) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = "Download")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("立即升级并更新")
                            }
                            if (downloadState is DownloadState.Error) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "下载失败: ${(downloadState as DownloadState.Error).message}",
                                    color = Color.Red,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            } else {
                // 已经是最新版本，展示绿色的小气泡
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(Color(0xFF2E7D32).copy(alpha = 0.1f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .background(Color(0xFF4CAF50))
                            .padding(4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "已是最新版，无须更新",
                        fontSize = 13.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
fun DeveloperDebugCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFC62828).copy(alpha = 0.06f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🛠️ 开发者调试沙盒 (无电脑测试专区)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFC62828)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "下面为您提供了一键触发运行时崩溃的能力。点击按钮将制造一次崩溃，以便您直观检测和体验 Crash 拦截堆栈解析界面的高级展示效果：",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    // 💡 故意制造致命崩溃，触发 GlobalExceptionHandler 拦截并拉起 CrashActivity
                    throw RuntimeException("这是一次开发者手动触发的测试崩溃！恭喜您，全局未捕获异常拦截系统运行完美！")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.BugReport, contentDescription = "Crash")
                Spacer(modifier = Modifier.width(6.dp))
                Text("触发致命测试崩溃")
            }
        }
    }
}

@Composable
fun DownloadProgressOverlay(downloadState: DownloadState) {
    AnimatedVisibility(
        visible = downloadState is DownloadState.Downloading || downloadState is DownloadState.Installing,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)) // 半透明遮挡，聚焦下载
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Downloading",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            Text(
                                text = "正在为您下载更新...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val progressFloat = if (downloadState.progress >= 0) downloadState.progress / 100f else 0f
                            LinearProgressIndicator(
                                progress = progressFloat,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "已完成: ${downloadState.progress}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (downloadState.totalBytes > 0) {
                                    val downloadedMB = String.format("%.2f", downloadState.downloadedBytes / (1024f * 1024f))
                                    val totalMB = String.format("%.2f", downloadState.totalBytes / (1024f * 1024f))
                                    Text(
                                        text = "$downloadedMB MB / $totalMB MB",
                                        fontSize = 13.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                        is DownloadState.Installing -> {
                            Text(
                                text = "下载完成！准备启动包管理器...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CircularProgressIndicator(modifier = Modifier.width(36.dp))
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}
