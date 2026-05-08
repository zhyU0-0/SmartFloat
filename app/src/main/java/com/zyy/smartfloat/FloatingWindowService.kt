package com.zyy.smartfloat

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
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
import com.zyy.smartfloat.prompt.ProcessHistory
import com.zyy.smartfloat.prompt.TapPoints
import com.zyy.smartfloat.prompt.buildLlmPrompt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

    // 语音识别相关 - 使用 VoiceRecognizer 封装类
    private var voiceRecognizer: VoiceRecognizer? = null
    private var isRecording = false
    private var longPressTriggered = false
    private val LONG_PRESS_THRESHOLD = 500L // 长按阈值500ms
    private val mainHandler = Handler(Looper.getMainLooper())
    private var floatingText: TextView? = null
    private var cleanButton: ImageView? = null
    private var submitButton: ImageView? = null
    private var recognizedText: String = ""
    private var isProcessing = false
    private var lastTapX: Double = -1.0
    private var lastTapY: Double = -1.0
    private var redButton: CardView? = null
    
    // 用于接收从 Intent 传递的问题
    private var llmQuestion: String = ""

    companion object {
        const val CHANNEL_ID = "floating_window_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.zyy.smartfloat.STOP_SERVICE"
        const val ACTION_LLM_RESULT = "com.zyy.smartfloat.LLM_RESULT"
        const val ACTION_TASK_START = "com.zyy.smartfloat.TASK_START"
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
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(ACTION_TASK_START))
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

            floatingText = floatView!!.findViewById(R.id.floating_text)
            cleanButton = floatView!!.findViewById(R.id.clean_floating_text_button)
            submitButton = floatView!!.findViewById(R.id.submit_floating_text_button)
            updateFloatingText()

            cleanButton?.setOnClickListener {
                if (recognizedText.isNotEmpty()) {
                    recognizedText = ""
                    updateFloatingText()
                }
            }

            submitButton?.setOnClickListener {
                if (recognizedText.isNotEmpty()) {
                    Toast.makeText(this@FloatingWindowService, "截图上传中...", Toast.LENGTH_SHORT).show()
                    captureAndUpload()
                }
            }

            redButton = floatView!!.findViewById<CardView>(R.id.floating_red_button)
            redButton?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        longPressTriggered = false
                        // 启动长按检测
                        mainHandler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD)
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
                        mainHandler.removeCallbacks(longPressRunnable)
                        
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)

                        if (longPressTriggered && isRecording) {
                            // 停止录音并识别
                            stopRecordingAndRecognize()
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

    private val longPressRunnable = Runnable {
        if (!isRecording && recognizedText.isEmpty() && !isProcessing) {
            startRecording()
        }
    }

    private fun startRecording() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "录音权限未授予")
            return
        }

        voiceRecognizer = VoiceRecognizer(this)
        voiceRecognizer?.setCallbacks(
            onResult = { text ->
                Log.d(TAG, "语音识别结果: $text")
                recognizedText = text
                isRecording = false
                mainHandler.post {
                    updateFloatingText()
                    resetButtonColor()
                }
            },
            onError = { error ->
                Log.e(TAG, "语音识别失败: $error")
                isRecording = false
                mainHandler.post {
                    resetButtonColor()
                    floatingText?.text = "🎤"
                }
            },
            onStart = {
                Log.d(TAG, "开始录音")
                isRecording = true
                longPressTriggered = true
                mainHandler.post {
                    // 改变按钮背景为红色表示录音中
                    floatView?.findViewById<CardView>(R.id.floating_red_button)?.setCardBackgroundColor(
                            Color.parseColor("#E53935")
                        )
                }
            }
        )
        
        voiceRecognizer?.startRecording()
    }

    private fun stopRecordingAndRecognize() {
        Log.d(TAG, "stopRecordingAndRecognize: 停止录音并识别")
        try {
            voiceRecognizer?.stopRecordingAndRecognize()
        } catch (e: Exception) {
            Log.e(TAG, "stopRecordingAndRecognize: 停止失败, error=${e.message}", e)
            isRecording = false
            resetButtonColor()
        }
    }

    private fun updateFloatingText() {
        if (recognizedText.isNotEmpty()) {
            floatingText?.text = recognizedText
            cleanButton?.visibility = View.VISIBLE
            submitButton?.visibility = View.VISIBLE
        } else {
            floatingText?.text = "🎤"
            cleanButton?.visibility = View.GONE
            submitButton?.visibility = View.GONE
        }
    }

    private fun resetButtonColor() {
        floatView?.findViewById<CardView>(R.id.floating_red_button)?.setCardBackgroundColor(
            Color.parseColor("#03A9F4")
        )
    }

    private fun captureAndUpload() {
        isProcessing = true
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

                withContext(Dispatchers.Main) {
                    redButton?.visibility = View.GONE
                }
                delay(100)

                val bitmap = a11yService.captureScreenshot()
                Log.d(TAG, "screenshot captured: ${bitmap.width}x${bitmap.height}")

                withContext(Dispatchers.Main) {
                    redButton?.visibility = View.VISIBLE
                }

                val file = saveBitmapToFile(bitmap)
                Log.d(TAG, "screenshot saved to: ${file.absolutePath}")

                val imageUrl = uploadScreenshot(file)
                Log.d(TAG, "upload success, url: $imageUrl")

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "上传成功: $imageUrl", Toast.LENGTH_LONG).show()
                }

                sendToLlm(imageUrl, recognizedText)
                
                // 上传完成后清空识别文本，以便下次重新录音
                mainHandler.post {
                    recognizedText = ""
                    updateFloatingText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureAndUpload failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingWindowService, "截图上传失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
                isProcessing = false
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun sendToLlm(imageUrl: String, question: String) {
        serviceScope.launch {
            val history = mutableListOf<ProcessHistory>()
            var currentImageUrl = imageUrl
            var loopCount = 0
            val maxLoops = 20

            while (loopCount < maxLoops) {
                loopCount++
                Log.d(TAG, "=== LLM循环第${loopCount}轮 ===")

                val lLmResponseExample = LLmResponse(
                    listOf(TapPoints(1.0, 10.0, 100)),
                    "用户指令",
                    false,
                    "简短的执行细节"
                )
                val gson = Gson()
                val json = gson.toJson(lLmResponseExample)
                val llmPrompt = buildLlmPrompt(this@FloatingWindowService, json)
                val llmBody = LLmBody(llmPrompt, question, screenWidth, screenHeight, history)

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
                                        image_url = ImageUrl(url = currentImageUrl)
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

                    val answer = response.choices.firstOrNull()?.message?.content ?: "返回：null"
                    Log.d(TAG, "LLM response: $answer")
                    val llmResponse = gson.fromJson(answer, LLmResponse::class.java)

                    llmResponse?.tapPoints?.forEach { tapPoint ->
                        Log.d(TAG, "执行点击: x=${tapPoint.tapX}, y=${tapPoint.tapY}, delay=${tapPoint.delay}")
                        lastTapX = tapPoint.tapX
                        lastTapY = tapPoint.tapY
                        TapAccessibilityService.instance?.simulateTap(
                            tapPoint.tapX.toFloat(),
                            tapPoint.tapY.toFloat(),
                            tapPoint.delay.toLong(),
                            1
                        )
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingWindowService, "LLM: ${llmResponse?.remark ?: answer}", Toast.LENGTH_SHORT).show()
                    }
                    sendLlmResultToActivity(llmResponse.remark ?: answer)
                    if (llmResponse?.isEnd == true) {
                        Log.d(TAG, "任务完成")
                        isProcessing = false
                        break
                    }

                    llmResponse?.remark?.let {
                        history.add(ProcessHistory(it,llmResponse.tapPoints))
                    }

                    if (loopCount < maxLoops) {
                        delay(1500)
                        withContext(Dispatchers.Main) {
                            redButton?.visibility = View.GONE
                        }
                        delay(100)

                        val bitmap = TapAccessibilityService.instance?.captureScreenshot()
                        
                        withContext(Dispatchers.Main) {
                            redButton?.visibility = View.VISIBLE
                        }

                        if (bitmap != null) {
                            val file = saveBitmapToFile(bitmap)
                            currentImageUrl = uploadScreenshot(file)
                            Log.d(TAG, "重新截图上传: $currentImageUrl")
                        } else {
                            Log.e(TAG, "重新截图失败")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "sendToLlm第${loopCount}轮失败: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingWindowService, "LLM请求失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    break
                }
            }

            if (loopCount >= maxLoops) {
                Log.w(TAG, "达到最大循环次数$maxLoops，强制结束")
                sendLlmResultToActivity("任务执行达到最大循环次数")
            }
            
            isProcessing = false
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

    private fun addGridAndCoordinates(bitmap: Bitmap, origWidth: Int, origHeight: Int, lastTapX: Double = -1.0, lastTapY: Double = -1.0): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val scaleX = width.toFloat() / origWidth
        val scaleY = height.toFloat() / origHeight
        
        val mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        val paint = Paint().apply {
            color = Color.parseColor("#88FF0000")
            strokeWidth = 1f
            isAntiAlias = true
        }

        val isLandscape = width > height
        val textScale = if (isLandscape) 0.6f else 1.0f
        val stepScale = if (isLandscape) 2 else 1

        val gridSize = 100
        val labelStep = 200

        for (x in 0..origWidth step gridSize) {
            val px = x * scaleX
            canvas.drawLine(px, 0f, px, height.toFloat(), paint)
        }

        for (y in 0..origHeight step gridSize) {
            val py = y * scaleY
            canvas.drawLine(0f, py, width.toFloat(), py, paint)
        }

        paint.color = Color.RED
        paint.textSize = 24f * textScale
        val labelTextSize = paint.textSize

        for (x in 0..origWidth step labelStep) {
            val px = x * scaleX
            canvas.drawText("$x", px-20, labelTextSize + 4, paint)
        }

        for (y in labelStep..origHeight step labelStep) {
            val py = y * scaleY
            canvas.drawText("$y", 5f, py - 4, paint)
        }

        paint.textSize = 24f * textScale
        paint.color = Color.GREEN
        val cornerTextSize = paint.textSize
        val cornerOffset = cornerTextSize + 8

        canvas.drawText("(0,0)", 5f, cornerOffset, paint)
        canvas.drawText("(0, $origHeight)", 5f, (height - 4).toFloat(), paint)
        canvas.drawText("($origWidth, $origHeight)", (width - cornerTextSize * 6).toFloat(), (height - 4).toFloat(), paint)

        if (lastTapX >= 0 && lastTapY >= 0) {
            val tapPx = lastTapX.toFloat() * scaleX
            val tapPy = lastTapY.toFloat() * scaleY
            paint.color = Color.parseColor("#88FF0000")
            paint.style = Paint.Style.FILL
            canvas.drawCircle(tapPx, tapPy, 15f, paint)
        }

        return mutableBitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File {
        val origWidth = bitmap.width
        val origHeight = bitmap.height
        val scaledBitmap = scaleDown(bitmap)
        val compressedBitmap = compressToLowQuality(scaledBitmap)
        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        val bitmapWithGrid = addGridAndCoordinates(compressedBitmap, origWidth, origHeight, lastTapX, lastTapY)
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