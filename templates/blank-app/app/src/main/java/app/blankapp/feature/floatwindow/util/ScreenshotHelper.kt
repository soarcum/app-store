package app.blankapp.feature.floatwindow.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 屏幕截图核心辅助工具类
 * 
 * 作用：利用 MediaProjection API 获取系统屏幕内容，处理行步长对齐（RowStride Padding），并安全保存图片。
 */
object ScreenshotHelper {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * 执行屏幕截图主逻辑
     */
    fun captureScreen(
        context: Context,
        resultCode: Int,
        resultData: Intent,
        onComplete: (Boolean) -> Unit
    ) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mediaProjection = projectionManager.getMediaProjection(resultCode, resultData) ?: run {
            onComplete(false)
            return
        }

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        val virtualDisplay = mediaProjection.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
            imageReader.surface,
            null,
            null
        )

        CoroutineScope(Dispatchers.Default).launch {
            delay(500)
            var image: Image? = null
            var screenshotBitmap: Bitmap? = null
            try {
                image = imageReader.acquireLatestImage()
                if (image != null) {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val tempBitmap = Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        Bitmap.Config.ARGB_8888
                    )
                    tempBitmap.copyPixelsFromBuffer(buffer)

                    screenshotBitmap = Bitmap.createBitmap(tempBitmap, 0, 0, width, height)
                    tempBitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                image?.close()
                virtualDisplay.release()
                imageReader.close()
                mediaProjection.stop()
            }

            if (screenshotBitmap != null) {
                val isSaved = saveBitmapToStorage(context, screenshotBitmap)
                withContext(Dispatchers.Main) {
                    if (isSaved) {
                        Toast.makeText(context, "截图成功，已保存至系统相册", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "截图失败，文件保存异常", Toast.LENGTH_SHORT).show()
                    }
                    onComplete(isSaved)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "未捕获到屏幕数据", Toast.LENGTH_SHORT).show()
                    onComplete(false)
                }
            }
        }
    }

    /**
     * 将 Bitmap 安全地保存至本地存储
     */
    private suspend fun saveBitmapToStorage(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        var outputStream: OutputStream? = null
        var success = false
        val filename = "Screenshot_${System.currentTimeMillis()}.png"

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/AppScreenshots")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (imageUri != null) {
                    outputStream = resolver.openOutputStream(imageUri)
                    if (outputStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                        success = true
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, contentValues, null, null)
                }
            } else {
                val imagesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                if (imagesDir != null) {
                    if (!imagesDir.exists()) {
                        imagesDir.mkdirs()
                    }
                    val imageFile = File(imagesDir, filename)
                    outputStream = FileOutputStream(imageFile)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    success = true

                    val mediaScanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                    val contentUri = Uri.fromFile(imageFile)
                    mediaScanIntent.data = contentUri
                    context.sendBroadcast(mediaScanIntent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            success = false
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            bitmap.recycle()
        }
        success
    }
}
