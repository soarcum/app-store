package com.slowpack.appstore

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.slowpack.appstore.core.crash.GlobalExceptionHandler
import com.slowpack.appstore.core.theme.AppTheme
import com.slowpack.appstore.feature.store.ui.AppStoreScreen

/**
 * 💡 应用程序主入口 MainActivity
 * 承载完全数据驱动的高质感 GitHub 私有应用商店容器
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
                    AppStoreScreen()
                }
            }
        }
    }
}
