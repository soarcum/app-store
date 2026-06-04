package app.blankapp.feature.floatwindow.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.blankapp.feature.floatwindow.service.FloatingService

/**
 * 屏幕截图授权透明中转 Activity
 */
class ScreenshotActivity : ComponentActivity() {

    private val requestScreenshotLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val resultCode = result.resultCode
        val data = result.data

        if (resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, FloatingService::class.java).apply {
                action = FloatingService.ACTION_TAKE_SCREENSHOT_RESULT
                putExtra(FloatingService.EXTRA_RESULT_CODE, resultCode)
                putExtra(FloatingService.EXTRA_RESULT_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            
            // 延迟 300ms 销毁透明中转 Activity，确保前台服务有充分的时间在系统 Binder 中注册投影，防止时序竞争导致 Binder Token 失效
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                finish()
                overridePendingTransition(0, 0)
            }, 300)
        } else {
            val cancelIntent = Intent(this, FloatingService::class.java).apply {
                action = FloatingService.ACTION_CANCEL_SCREENSHOT
            }
            startService(cancelIntent)
            finish()
            overridePendingTransition(0, 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        overridePendingTransition(0, 0)

        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            val captureIntent = projectionManager.createScreenCaptureIntent()
            requestScreenshotLauncher.launch(captureIntent)
        } catch (e: Exception) {
            e.printStackTrace()
            val cancelIntent = Intent(this, FloatingService::class.java).apply {
                action = FloatingService.ACTION_CANCEL_SCREENSHOT
            }
            startService(cancelIntent)
            finish()
        }
    }
}
