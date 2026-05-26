package app.blankapp.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.blankapp.core.update.UpdateDialog
import app.blankapp.core.update.UpdateManager

@Composable
fun HomeScreen() {
    // 当 HomeScreen 初次进入组合树生命周期时，自动触发静默更新检查
    LaunchedEffect(Unit) {
        UpdateManager.checkUpdate()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 主页面的主体内容
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Hello, World!",
                style = MaterialTheme.typography.headlineLarge
            )
        }

        // 挂载磨砂玻璃质感的自更新交互弹窗
        UpdateDialog()
    }
}

