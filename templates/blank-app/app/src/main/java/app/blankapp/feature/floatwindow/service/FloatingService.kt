package app.blankapp.feature.floatwindow.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.blankapp.MainActivity
import app.blankapp.core.theme.AppTheme
import app.blankapp.feature.floatwindow.ui.FloatingWidget
import app.blankapp.feature.floatwindow.ui.ScreenshotActivity
import app.blankapp.feature.floatwindow.util.NotificationHelper
import app.blankapp.feature.floatwindow.util.ScreenshotHelper
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * 悬浮窗核心前台服务
 */
class FloatingService : Service(), ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        var isServiceRunning = false

        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_TAKE_SCREENSHOT = "ACTION_TAKE_SCREENSHOT"
        const val ACTION_TAKE_SCREENSHOT_RESULT = "ACTION_TAKE_SCREENSHOT_RESULT"
        const val ACTION_CANCEL_SCREENSHOT = "ACTION_CANCEL_SCREENSHOT"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var composeView: ComposeView

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        val notification = NotificationHelper.buildNotification(this)
        startForeground(NotificationHelper.NOTIFICATION_ID, notification)

        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        initLayoutParams()
        initComposeView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        val action = intent?.action ?: ACTION_START

        when (action) {
            ACTION_START -> {
                showFloatingWindow()
            }
            ACTION_TAKE_SCREENSHOT -> {
                hideFloatingWindow()
                val startActIntent = Intent(this, ScreenshotActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(startActIntent)
            }
            ACTION_TAKE_SCREENSHOT_RESULT -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != 0 && resultData != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val notification = NotificationHelper.buildNotification(this)
                        startForeground(
                            NotificationHelper.NOTIFICATION_ID,
                            notification,
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        )
                    }
                    ScreenshotHelper.captureScreen(this, resultCode, resultData) { success ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            val notification = NotificationHelper.buildNotification(this)
                            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
                        }
                        showFloatingWindow()
                    }
                } else {
                    showFloatingWindow()
                }
            }
            ACTION_CANCEL_SCREENSHOT -> {
                showFloatingWindow()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun initLayoutParams() {
        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            val metrics = resources.displayMetrics
            x = metrics.widthPixels - 150
            y = metrics.heightPixels / 2
        }
    }

    private fun initComposeView() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@FloatingService)
            setViewTreeViewModelStoreOwner(this@FloatingService)
            setViewTreeSavedStateRegistryOwner(this@FloatingService)

            setContent {
                AppTheme {
                    FloatingWidget(
                        onDrag = { dx, dy ->
                            updateWindowPosition(this@FloatingService.layoutParams.x + dx, this@FloatingService.layoutParams.y + dy)
                        },
                        onDragEnd = {
                            performSnappingAnimation()
                        },
                        onActionScreenshot = {
                            val startSelfIntent = Intent(this@FloatingService, FloatingService::class.java).apply {
                                action = ACTION_TAKE_SCREENSHOT
                            }
                            startService(startSelfIntent)
                        },
                        onActionClose = {
                            stopSelf()
                        },
                        onActionBackToApp = {
                            val mainIntent = Intent(this@FloatingService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            }
                            startActivity(mainIntent)
                        }
                    )
                }
            }
        }
    }

    private fun updateWindowPosition(x: Int, y: Int) {
        if (::composeView.isInitialized && composeView.parent != null) {
            val metrics = resources.displayMetrics
            layoutParams.x = x.coerceIn(0, metrics.widthPixels - composeView.width)
            layoutParams.y = y.coerceIn(0, metrics.heightPixels - composeView.height)
            windowManager.updateViewLayout(composeView, layoutParams)
        }
    }

    private fun performSnappingAnimation() {
        if (!::composeView.isInitialized || composeView.parent == null) return
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val viewWidth = composeView.width

        val targetX = if (layoutParams.x + viewWidth / 2 < screenWidth / 2) {
            0
        } else {
            screenWidth - viewWidth
        }

        val animator = ValueAnimator.ofInt(layoutParams.x, targetX).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val animX = animation.animatedValue as Int
                updateWindowPosition(animX, layoutParams.y)
            }
        }
        animator.start()
    }

    private fun hideFloatingWindow() {
        if (::composeView.isInitialized && composeView.parent != null) {
            composeView.visibility = View.GONE
        }
    }

    private fun showFloatingWindow() {
        if (::composeView.isInitialized) {
            if (composeView.parent == null) {
                windowManager.addView(composeView, layoutParams)
            } else {
                composeView.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        isServiceRunning = false
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (::composeView.isInitialized && composeView.parent != null) {
            windowManager.removeView(composeView)
        }
        super.onDestroy()
    }
}
