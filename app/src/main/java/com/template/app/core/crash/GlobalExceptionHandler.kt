package com.template.app.core.crash

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * 💡 全局崩溃拦截器 (无电脑真机调试核心利器)
 * 接管未捕获异常，阻断闪退，将异常堆栈传递给独立的 CrashActivity 展示
 */
class GlobalExceptionHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"

        fun register(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            // 防止重复注册
            if (defaultHandler is GlobalExceptionHandler) return
            
            val handler = GlobalExceptionHandler(context.applicationContext, defaultHandler)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            // 1. 将 throwable 转化为详细的堆栈字符串
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            var stackTrace = sw.toString()

            // 限制传递堆栈大小，防止 Intent 溢出 (Binder 传输限制 1MB)
            if (stackTrace.length > 20000) {
                stackTrace = stackTrace.substring(0, 20000) + "\n... [已截断，堆栈过长]"
            }

            Log.e(TAG, "检测到未捕获崩溃: $stackTrace")

            // 2. 构造启动 CrashActivity 的 Intent
            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_INFO, stackTrace)
                putExtra(CrashActivity.EXTRA_THREAD_NAME, thread.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "崩溃拦截器自身异常: ${e.message}")
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            // 3. 杀掉主进程，退出应用，交由独立进程 :crash 稳定显示崩溃界面
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
