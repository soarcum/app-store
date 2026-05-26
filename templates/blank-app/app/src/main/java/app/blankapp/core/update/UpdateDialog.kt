package app.blankapp.core.update

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 精美优雅的自更新弹框 (Jetpack Compose 驱动)
 * 采用现代的半透明磨砂玻璃卡片视觉 (Glassmorphism) 与高级 HSL 渐变调色系统，
 * 下载时呈现炫美的流光渲染动画进度条。
 */
@Composable
fun UpdateDialog() {
    val show = UpdateManager.showDialog
    val state = UpdateManager.updateState
    val context = LocalContext.current

    if (!show) return

    // 状态为 Idle 或者是 NoUpdate 时，不进行渲染
    if (state is UpdateState.Idle || state is UpdateState.NoUpdate) return

    Dialog(
        onDismissRequest = {
            // 当不是正在强制下载时，允许点击外部或物理返回键关闭弹窗
            if (state !is UpdateState.Downloading) {
                UpdateManager.showDialog = false
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = state !is UpdateState.Downloading,
            dismissOnClickOutside = state !is UpdateState.Downloading,
            usePlatformDefaultWidth = false // 禁用平台默认宽度以便实现全边距自定义弹窗
        )
    ) {
        var animateTrigger by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            animateTrigger = true
        }

        // 卡片弹性微缩放与淡入物理模拟动效，充满生命力
        val scale by animateFloatAsState(
            targetValue = if (animateTrigger) 1f else 0.85f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            ),
            label = "scale"
        )
        val alpha by animateFloatAsState(
            targetValue = if (animateTrigger) 1f else 0f,
            animationSpec = tween(300),
            label = "alpha"
        )

        Box(
            modifier = Modifier
                .padding(28.dp)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    alpha = alpha
                ),
            contentAlignment = Alignment.Center
        ) {
            // 炫美的渐变炫光外发光背景层
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(alpha = 0.12f)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF6C63FF),
                                Color(0xFF3F51B5),
                                Color.Transparent
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
            )

            // 磨砂玻璃主体卡片
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x66FFFFFF),
                                Color(0x1AFFFFFF)
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xF41E1E24) // 质感高级的半透明深灰背景
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 顶部头部标志区域
                    HeaderSection(state = state)

                    Spacer(modifier = Modifier.height(20.dp))

                    // 内容区 (根据不同状态差异化显示)
                    when (state) {
                        is UpdateState.NewVersionAvailable -> {
                            VersionInfoContent(
                                version = state.versionName,
                                notes = state.releaseNotes,
                                size = state.fileSize
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // 底部操作按钮
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                OutlinedButton(
                                    onClick = { UpdateManager.showDialog = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "稍后更新",
                                        color = Color(0xB3FFFFFF)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Button(
                                    onClick = { UpdateManager.downloadApk(context, state.downloadUrl) },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6C63FF)
                                    )
                                ) {
                                    Text(
                                        text = "立即更新",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is UpdateState.Downloading -> {
                            DownloadProgressContent(
                                progress = state.progress,
                                downloadedBytes = state.downloadedBytes,
                                totalBytes = state.totalBytes
                            )
                        }

                        is UpdateState.ReadyToInstall -> {
                            InstallContent()

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { UpdateManager.showDialog = false },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "取消",
                                        color = Color(0xB3FFFFFF)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Button(
                                    onClick = { UpdateManager.installApk(context, state.apkUri) },
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF00C853)
                                    )
                                ) {
                                    Text(
                                        text = "立即安装",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        is UpdateState.Error -> {
                            ErrorContent(message = state.message)

                            Spacer(modifier = Modifier.height(24.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { UpdateManager.resetState() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "关闭",
                                        color = Color(0xB3FFFFFF)
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Button(
                                    onClick = { UpdateManager.checkUpdate() },
                                    modifier = Modifier.weight(1.5f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF6C63FF)
                                    )
                                ) {
                                    Text(
                                        text = "重新检测",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        else -> {}
                    }
                }
            }
        }
    }
}

/**
 * 对话框顶部图标与大标题区域
 */
@Composable
private fun HeaderSection(state: UpdateState) {
    val icon = when (state) {
        is UpdateState.Downloading -> Icons.Default.CloudDownload
        is UpdateState.ReadyToInstall -> Icons.Default.SystemUpdate
        is UpdateState.Error -> Icons.Default.ErrorOutline
        else -> Icons.Default.Info
    }

    val tintColor = when (state) {
        is UpdateState.ReadyToInstall -> Color(0xFF00C853)
        is UpdateState.Error -> Color(0xFFFF5252)
        else -> Color(0xFF6C63FF)
    }

    val title = when (state) {
        is UpdateState.Downloading -> "正在安全下载新版本..."
        is UpdateState.ReadyToInstall -> "新版本已下载完毕"
        is UpdateState.Error -> "自更新遇到了一些问题"
        else -> "发现重要更新"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tintColor,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            ),
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 发现新版本时的界面内容
 */
@Composable
private fun VersionInfoContent(
    version: String,
    notes: String,
    size: Long
) {
    val sizeText = if (size > 0) {
        val mb = size.toDouble() / (1024 * 1024)
        String.format(" (%.2f MB)", mb)
    } else {
        ""
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "最新版本: $version$sizeText",
                color = Color(0xFF6C63FF),
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "更新日志:",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = Color(0xCCFFFFFF),
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // 柔和优雅的滚动更新日志区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(0x0CFFFFFF), RoundedCornerShape(12.dp))
                .border(0.5.dp, Color(0x12FFFFFF), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = Color(0xBBFFFFFF)
                )
            }
        }
    }
}

/**
 * 下载进度呈现与流光进度条组件
 */
@Composable
private fun DownloadProgressContent(
    progress: Int,
    downloadedBytes: Long,
    totalBytes: Long
) {
    val currentMb = downloadedBytes.toDouble() / (1024 * 1024)
    val totalMb = totalBytes.toDouble() / (1024 * 1024)

    val subtitle = if (totalBytes > 0) {
        String.format("%.2f MB / %.2f MB", currentMb, totalMb)
    } else {
        String.format("%.2f MB已下载", currentMb)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 精美的流光指示条背景
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0x1EFFFFFF))
        ) {
            // 无尽平移动画，创造跑马灯流光的科幻视觉
            val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
            val offsetAnim by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1200f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "offset"
            )

            // 炫美的多重渐变流光 Brush
            val flowingBrush = Brush.linearGradient(
                colors = listOf(
                    Color(0xFF6C63FF),
                    Color(0xFFB388FF),
                    Color(0xFF00E5FF),
                    Color(0xFF6C63FF)
                ),
                start = Offset(offsetAnim - 300f, 0f),
                end = Offset(offsetAnim, 0f),
                tileMode = TileMode.Repeated
            )

            // 根据下载进度动态更新的进度槽
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.toFloat() / 100f)
                    .background(flowingBrush)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color(0x99FFFFFF)
            )

            Text(
                text = "$progress%",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * 准备安装时的静态文字提示
 */
@Composable
private fun InstallContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "新版本软件包已全部下载完成并且已通过完整性校验，请点击下方“立即安装”升级您的应用。",
            color = Color(0xBBFFFFFF),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

/**
 * 发生错误时的提示区域
 */
@Composable
private fun ErrorContent(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = message,
            color = Color(0xFFFF8A80),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x15FF5252), RoundedCornerShape(10.dp))
                .padding(14.dp)
        )
    }
}
