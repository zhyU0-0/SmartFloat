package com.zyy.smartfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.zyy.smartfloat.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isShowing = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "floating_window_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.zyy.smartfloat.STOP_SERVICE"
        private const val TAG = "FloatingWindowService"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopService()
            return START_NOT_STICKY
        }
        try {
            startForeground(NOTIFICATION_ID, createNotification())
        } catch (e: SecurityException) {
            Log.e(TAG, "startForeground failed: ${e.message}")
            stopService()
            return START_NOT_STICKY
        }
        if (!isShowing) {
            showFloatingWindow()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        hideFloatingWindow()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, FloatingWindowService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_service_notification))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "隐藏", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun showFloatingWindow() {
        try {
            val wm = windowManager ?: return

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val layoutParams = WindowManager.LayoutParams().apply {
                this.type = type
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }

            val inflater = LayoutInflater.from(applicationContext)
            floatView = inflater.inflate(R.layout.floating_button, null)

            val redButton = floatView!!.findViewById<FrameLayout>(R.id.floating_red_button)
            redButton.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        wm.updateViewLayout(floatView, layoutParams)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        if (deltaX < 10 && deltaY < 10) {
                            Toast.makeText(this@FloatingWindowService, "截图上传中...", Toast.LENGTH_SHORT).show()
                            captureAndUpload()
                        }
                        true
                    }
                    else -> false
                }
            }

            wm.addView(floatView, layoutParams)
            Log.d(TAG, "floatView added to window, type=$type")
            isShowing = true
        } catch (e: Exception) {
            Log.e(TAG, "showFloatingWindow failed: ${e.message}", e)
        }
    }

    private fun captureAndUpload() {
        serviceScope.launch {
            try {
                val a11yService = TapAccessibilityService.instance
                if (a11yService == null) {
                    Log.e(TAG, "TapAccessibilityService instance is null")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingWindowService, "无障碍服务未连接", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Log.e(TAG, "takeScreenshot requires API 34+")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingWindowService, "当前系统不支持无障碍截图(需Android 14+)", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val bitmap = a11yService.captureScreenshot()
                Log.d(TAG, "screenshot captured: ${bitmap.width}x${bitmap.height}")

                val file = saveBitmapToFile(bitmap)
                Log.d(TAG, "screenshot saved to: ${file.absolutePath}")

                val url = uploadScreenshot(file)
                Log.d(TAG, "upload success, url: $url")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "上传成功: $url", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureAndUpload failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "截图上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val dir = cacheDir
        val file = File(dir, "smartFloat.png")
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmap.recycle()
        return file
    }

    private suspend fun uploadScreenshot(file: File): String {
        val requestBody = file.asRequestBody("image/png".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", file.name, requestBody)
        val response = RetrofitClient.uploadApi.uploadFile(part)
        Log.d(TAG, "upload response: code=${response.code}, message=${response.message}, data=${response.data}")
        return response.data ?: "no url returned"
    }

    private fun hideFloatingWindow() {
        try {
            if (isShowing && floatView != null) {
                windowManager?.removeView(floatView)
                isShowing = false
                floatView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "hideFloatingWindow failed: ${e.message}")
        }
    }

    private fun stopService() {
        hideFloatingWindow()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
