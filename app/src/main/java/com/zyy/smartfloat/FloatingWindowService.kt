package com.zyy.smartfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.zyy.smartfloat.network.ImageUrl
import com.zyy.smartfloat.network.LlmContent
import com.zyy.smartfloat.network.LlmMessage
import com.zyy.smartfloat.network.LlmRequest
import com.zyy.smartfloat.network.RetrofitClient
import com.zyy.smartfloat.prompt.LLmBody
import com.zyy.smartfloat.prompt.LLmResponse
import com.zyy.smartfloat.prompt.TapPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
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
    private var screenWidth = 0
    private var screenHeight = 0


    companion object {
        const val CHANNEL_ID = "floating_window_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.zyy.smartfloat.STOP_SERVICE"
        const val ACTION_LLM_RESULT = "com.zyy.smartfloat.LLM_RESULT"
        const val EXTRA_LLM_RESULT = "extra_llm_result"
        const val EXTRA_LLM_QUESTION = "extra_llm_question"
        private const val TAG = "FloatingWindowService"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        getScreenSize()
        Log.d(TAG, "Screen size: $screenWidth x $screenHeight")
    }

    private fun getScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        Log.d(TAG, "Screen width: $screenWidth, height: $screenHeight")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopService()
            return START_NOT_STICKY
        }
        
        intent?.getStringExtra(EXTRA_LLM_QUESTION)?.let {
            if (it.isNotEmpty()) {
                llmQuestion = it
                Log.d(TAG, "LLM question updated: $llmQuestion")
            }
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
                        Log.d(TAG, "button is MOVE ("+event.rawX+" , "+event.rawY+")")
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

                val imageUrl = uploadScreenshot(file)
                Log.d(TAG, "upload success, url: $imageUrl")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "上传成功: $imageUrl", Toast.LENGTH_LONG).show()
                }

                sendToLlm(imageUrl)
            } catch (e: Exception) {
                Log.e(TAG, "captureAndUpload failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "截图上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private var llmQuestion = "点击返回按钮"

    private fun sendToLlm(imageUrl: String) {
        serviceScope.launch {
            val lLmResponse = LLmResponse(listOf(TapPoints(1.0, 10.0, 100)),"用户最终指令",false,"简短的执行细节")
            val gson = Gson()
            val json = gson.toJson(lLmResponse)
            val llmPrompt = "你是专业的坐标测定和点击操作工具，严格按以下规则执行：\n" +
                    "\n" +
                    "1. 坐标系：根据图中红色的网格和数值确定坐标\n" +
                    "   原点：图片左上角 (0,0)\n" +
                    "   X轴：从左到右 0~maxX\n" +
                    "   Y轴：从上到下 0~maxY\n" +
                    "\n" +
                    "2. 定位规则\n" +
                    "   - 先在图中找到用户指定的目标\n" +
                    "   - 根据图中标注的红色的网格和数值，确定目标的x和y作为tapX和tapY" +
                    "   - 坐标必须为整数\n" +
                    "   - 禁止凭经验猜测，必须基于当前图片真实位置\n" +
                    "\n" +
                    "3. 输出格式"+json +
                    "\n" +"4. remark就显示一简短的结果，isEnd就是任务是否完成，command就是用户的指令"
            val llmBody = LLmBody(llmPrompt, llmQuestion, screenWidth, screenHeight)

            try {
                val apiKey = getString(R.string.llm_api_key)
                val model = getString(R.string.llm_model)

                val request = LlmRequest(
                    model = model,
                    messages = listOf(
                        LlmMessage(
                            role = "user",
                            content = listOf(
                                LlmContent(
                                    type = "image_url",
                                    image_url = ImageUrl(url = imageUrl)
                                ),
                                LlmContent(
                                    type = "text",
                                    text = gson.toJson(llmBody)
                                )
                            )
                        )
                    )
                )

                val response = RetrofitClient.llmApi.chatCompletion(
                    auth = "Bearer $apiKey",
                    request = request
                )

                val answer = response.choices.firstOrNull()?.message?.content?:"返回：null"
                Log.d(TAG, "LLM response: $answer")
                val llmResponse = gson.fromJson(answer, LLmResponse::class.java)

                llmResponse?.tapPoints?.forEach { tapPoint ->
                    Log.d(TAG, "执行点击: x=${tapPoint.tapX}, y=${tapPoint.tapY}, delay=${tapPoint.delay}")
                    TapAccessibilityService.instance?.simulateTap(
                        tapPoint.tapX.toFloat(),
                        tapPoint.tapY.toFloat(),
                        tapPoint.delay.toLong(),
                        1
                    )
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "LLM回答: ${llmResponse?.remark ?: answer}", Toast.LENGTH_LONG).show()
                }

                sendLlmResultToActivity(llmResponse?.remark ?: answer)
            } catch (e: Exception) {
                Log.e(TAG, "sendToLlm failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "LLM请求失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun sendLlmResultToActivity(result: String) {
        val intent = Intent(ACTION_LLM_RESULT)
        intent.putExtra(EXTRA_LLM_RESULT, result)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }


    private fun scaleDown(bitmap: Bitmap, maxWidth: Int = 540, maxHeight: Int = 1200): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxWidth && height <= maxHeight) return bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun compressToLowQuality(bitmap: Bitmap, quality: Int = 30): Bitmap {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val bais = ByteArrayInputStream(baos.toByteArray())
        return android.graphics.BitmapFactory.decodeStream(bais)!!
    }

    private fun addGridAndCoordinates(bitmap: Bitmap, origWidth: Int, origHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scaleX = width.toFloat() / origWidth
        val scaleY = height.toFloat() / origHeight
        
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.parseColor("#88FF0000")
            strokeWidth = 1f
            textSize = 30f
            isAntiAlias = true
        }

        val gridSize = 50
        val labelStep = 100

        for (x in 0..origWidth step gridSize) {
            val px = x * scaleX
            canvas.drawLine(px, 0f, px, height.toFloat(), paint)
        }

        for (y in 0..origHeight step gridSize) {
            val py = y * scaleY
            canvas.drawLine(0f, py, width.toFloat(), py, paint)
        }

        paint.color = Color.RED
        paint.textSize = 24f

        for (x in 0..origWidth step labelStep) {
            val px = x * scaleX
            canvas.drawText("$x", px, 30f, paint)
        }

        for (y in labelStep..origHeight step labelStep) {
            val py = y * scaleY
            canvas.drawText("$y", 5f, py, paint)
        }

        paint.textSize = 32f
        paint.color = Color.GREEN
        canvas.drawText("(0,0)", 5f, 60f, paint)
        canvas.drawText("($origWidth, 0)", (width - 120).toFloat(), 60f, paint)
        canvas.drawText("(0, $origHeight)", 5f, (height - 10).toFloat(), paint)
        canvas.drawText("($origWidth, $origHeight)", (width - 160).toFloat(), (height - 10).toFloat(), paint)

        return mutableBitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val origWidth = bitmap.width
        val origHeight = bitmap.height
        val scaledBitmap = scaleDown(bitmap)
        val compressedBitmap = compressToLowQuality(scaledBitmap)
        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        val bitmapWithGrid = addGridAndCoordinates(compressedBitmap, origWidth, origHeight)
        compressedBitmap.recycle()
        val dir = cacheDir
        val file = File(dir, "smartFloat.png")
        FileOutputStream(file).use { fos ->
            bitmapWithGrid.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmapWithGrid.recycle()
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