package app.blankapp.core.crash

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

class GlobalExceptionHandler private constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"

        fun register(context: Context) {
            val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (defaultHandler is GlobalExceptionHandler) return

            val handler = GlobalExceptionHandler(context.applicationContext, defaultHandler)
            Thread.setDefaultUncaughtExceptionHandler(handler)
        }
    }

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            var stackTrace = sw.toString()

            if (stackTrace.length > 20000) {
                stackTrace = stackTrace.substring(0, 20000) + "\n... [truncated]"
            }

            Log.e(TAG, "Uncaught exception: $stackTrace")

            val intent = Intent(context, CrashActivity::class.java).apply {
                putExtra(CrashActivity.EXTRA_CRASH_INFO, stackTrace)
                putExtra(CrashActivity.EXTRA_THREAD_NAME, thread.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            context.startActivity(intent)

        } catch (e: Exception) {
            Log.e(TAG, "Crash handler error: ${e.message}")
            defaultHandler?.uncaughtException(thread, throwable)
        } finally {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }
}
