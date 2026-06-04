package app.blankapp.feature.floatwindow.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import app.blankapp.MainActivity

/**
 * 悬浮窗前台服务通知辅助工具类
 * 
 * 作用：为 FloatingService 提供常驻前台所必需的通知（Notification）及通知通道（NotificationChannel）。
 */
object NotificationHelper {

    private const val CHANNEL_ID = "sparkai_float_window_channel"
    private const val CHANNEL_NAME = "SparkAI 悬浮窗服务"
    private const val CHANNEL_DESC = "用于保持 SparkAI 悬浮窗后台常驻的系统通知"
    const val NOTIFICATION_ID = 10086

    /**
     * 创建前台服务通知
     */
    fun buildNotification(context: Context): Notification {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("常驻屏幕助手已启用")
            .setContentText("悬浮面板常驻运行中，点击可返回应用")
            .setSmallIcon(android.R.drawable.ic_menu_compass) 
            .setContentIntent(pendingIntent)
            .setOngoing(true) 
            .setPriority(NotificationCompat.PRIORITY_LOW) 
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /**
     * 创建并注册通知通道
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = CHANNEL_DESC
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_SECRET
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}
