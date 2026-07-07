package com.example.memo.autoui.service

import android.app.Service
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
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import timber.log.Timber
import java.nio.ByteBuffer

class ScreenCaptureService : Service() {

    companion object {
        private var instance: ScreenCaptureService? = null
        private var mediaProjection: MediaProjection? = null
        private var virtualDisplay: VirtualDisplay? = null
        private var imageReader: ImageReader? = null
        private var resultCode = 0
        private var resultData: Intent? = null

        fun setResultData(code: Int, data: Intent) {
            resultCode = code
            resultData = data
        }

        fun getInstance(): ScreenCaptureService? = instance

        fun isReady(): Boolean = instance != null && mediaProjection != null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    fun startCapture(context: Context): Boolean {
        if (mediaProjection != null) return true

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE)
            as MediaProjectionManager

        val data = resultData ?: return false
        val projection = projectionManager.getMediaProjection(resultCode, data) ?: return false
        mediaProjection = projection

        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val reader = imageReader ?: return false
        virtualDisplay = projection.createVirtualDisplay(
            "ScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null, null
        )

        return true
    }

    fun captureScreenshot(): Bitmap? {
        val reader = imageReader ?: return null

        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image == null) return null

            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            return Bitmap.createBitmap(
                bitmap, 0, 0,
                image.width, image.height
            )
        } catch (e: Exception) {
            Timber.e(e, "截屏处理异常")
            return null
        } finally {
            image?.close()
        }
    }

    fun stopCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }
}
