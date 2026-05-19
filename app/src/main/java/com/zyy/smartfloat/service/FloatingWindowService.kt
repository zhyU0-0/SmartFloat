package com.zyy.smartfloat.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zyy.smartfloat.MainActivity
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.R
import com.zyy.smartfloat.utils.VoiceRecognizer
import com.zyy.smartfloat.network.ImageUrl
import com.zyy.smartfloat.network.LLmThinkingType
import com.zyy.smartfloat.network.LlmContent
import com.zyy.smartfloat.network.LlmMessage
import com.zyy.smartfloat.network.LlmRequest
import com.zyy.smartfloat.network.RetrofitClient
import com.zyy.smartfloat.prompt.LLmBody
import com.zyy.smartfloat.prompt.LLmResponse
import com.zyy.smartfloat.prompt.ProcessHistory
import com.zyy.smartfloat.prompt.TapPoints
import com.zyy.smartfloat.prompt.buildLlmPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicLong

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
    val maxLoops = 100

    // 语音识别相关 - 使用 VoiceRecognizer 封装类
    private var voiceRecognizer: VoiceRecognizer? = null
    private var isRecording = false
    private var longPressTriggered = false
    private val LONG_PRESS_THRESHOLD = 500L // 长按阈值500ms
    private val mainHandler = Handler(Looper.getMainLooper())
    private var floatingText: EditText? = null
    private var floatingIcon: ImageView? = null
    private var cleanButton: ImageView? = null
    private var editButton: ImageView? = null
    private var submitButton: ImageView? = null
    private var isEditing = false
    private var recognizedText: String = ""
    private var isProcessing = false
    private val currentTaskId = AtomicLong(0)
    private var lastTapPoints = mutableListOf<TapPoints>()
    private var redButton: CardView? = null
    private var floatLayoutParams: WindowManager.LayoutParams? = null

    private val prefs by lazy { getSharedPreferences("SmartFloatSettings", MODE_PRIVATE) }
    private var cachedImagePath: String? = null

    // 用于接收从 Intent 传递的问题
    private var llmQuestion: String = ""
    private var isEnableImage: Boolean = false
    private var isEnableEnglish: Boolean = false
    
    private fun refreshSettings() {
        isEnableImage = prefs.getBoolean("enable_image", false)
        isEnableEnglish = prefs.getBoolean("enable_english", false)
        Log.d(TAG, "Settings refreshed: isEnableImage=$isEnableImage, isEnableEnglish=$isEnableEnglish")
    }

    companion object {
        const val CHANNEL_ID = "floating_window_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.zyy.smartfloat.STOP_SERVICE"
        const val ACTION_LLM_RESULT = "com.zyy.smartfloat.LLM_RESULT"
        const val ACTION_TASK_START = "com.zyy.smartfloat.TASK_START"
        const val ACTION_VOICE_RECOGNITION = "com.zyy.smartfloat.VOICE_RECOGNITION"
        const val ACTION_IMAGE_RECOGNITION = "com.zyy.smartfloat.IMAGE_RECOGNITION"
        const val EXTRA_LLM_RESULT = "extra_llm_result"
        const val EXTRA_LLM_QUESTION = "extra_llm_question"
        const val EXTRA_VOICE_TEXT = "extra_voice_text"
        const val EXTRA_IMAGE_TEXT = "extra_image_text"
        private const val TAG = "FloatingWindowService"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        getScreenSize()
        refreshSettings()
        Log.d(TAG, "Screen size: $screenWidth x $screenHeight")
    }

    private fun getScreenSize() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
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
            floatLayoutParams = layoutParams

            val inflater = LayoutInflater.from(applicationContext)
            floatView = inflater.inflate(R.layout.floating_button, null)

            floatingText = floatView!!.findViewById(R.id.floating_text)
            floatingIcon = floatView!!.findViewById(R.id.floating_icon)
            cleanButton = floatView!!.findViewById(R.id.clean_floating_text_button)
            editButton = floatView!!.findViewById(R.id.edit_floating_text_button)
            submitButton = floatView!!.findViewById(R.id.submit_floating_text_button)
            updateFloatingText()

            floatingText?.setOnFocusChangeListener { _, hasFocus ->
                val params = floatLayoutParams ?: return@setOnFocusChangeListener
                if (hasFocus) {
                    params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                    editButton?.setImageResource(R.drawable.ic_complete)
                } else {
                    params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                }
                windowManager?.updateViewLayout(floatView, params)
            }

            editButton?.setOnClickListener {
                isEditing = !isEditing
                if (isEditing) {
                    // 进入编辑模式
                    floatingText?.visibility = View.VISIBLE
                    floatingIcon?.visibility = View.GONE
                    floatingText?.requestFocus()
                    // 显示软键盘
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(floatingText, InputMethodManager.SHOW_IMPLICIT)
                    editButton?.setImageResource(R.drawable.ic_complete)
                } else {
                    // 退出编辑模式
                    floatingText?.clearFocus()
                    recognizedText = floatingText?.text?.toString() ?: ""
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(floatingText?.windowToken, 0)
                    updateFloatingText()
                    editButton?.setImageResource(R.drawable.ic_edit)
                }
            }

            cleanButton?.setOnClickListener {
                // 清空文本
                floatingText?.text = null
                recognizedText = ""
                // 退出编辑模式
                isEditing = false
                floatingText?.clearFocus()
                // 隐藏软键盘
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(floatingText?.windowToken, 0)
                // 更新UI回到原始状态
                updateFloatingText()
                Log.d(TAG,"clean Editing")
            }

            submitButton?.setOnClickListener {
                if (recognizedText.isNotEmpty()) {
                    // 取消焦点和编辑模式
                    floatingText?.clearFocus()
                    isEditing = false
                    refreshSettings()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(floatingText?.windowToken, 0)
                    recognizedText = floatingText?.text?.toString() ?: ""
                    Toast.makeText(this@FloatingWindowService, "处理中...", Toast.LENGTH_SHORT).show()
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
                        // 如果正在处理中，不启动长按检测，准备处理点击取消
                        if (!isProcessing) {
                            mainHandler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD)
                        }
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

                        if (isProcessing) {
                            // 如果正在处理中，点击按钮取消任务
                            cancelProcessing()
                        } else if (longPressTriggered && isRecording) {
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
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
                sendVoiceRecognitionResult(text)
                mainHandler.post {
                    updateFloatingText()
                    resetButtonColor()
                }
            },
            onError = { error ->
                Log.e(TAG, "语音识别失败: $error")
                isRecording = false
                sendVoiceRecognitionResult(error)
                mainHandler.post {
                    resetButtonColor()
                    updateFloatingText()
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
        if (isEditing) {
            // 编辑模式：显示EditText
            floatingText?.visibility = View.VISIBLE
            floatingIcon?.visibility = View.GONE
            cleanButton?.visibility = View.VISIBLE
            editButton?.visibility = View.VISIBLE
            submitButton?.visibility = View.VISIBLE
        } else if (recognizedText.isNotEmpty()) {
            // 有文本但不在编辑模式
            floatingText?.setText(recognizedText)
            floatingText?.visibility = View.VISIBLE
            floatingIcon?.visibility = View.GONE
            cleanButton?.visibility = View.VISIBLE
            editButton?.visibility = View.VISIBLE
            submitButton?.visibility = View.VISIBLE
        } else {
            // 无文本且不在编辑模式
            floatingText?.visibility = View.GONE
            floatingIcon?.visibility = View.VISIBLE
            if (isProcessing) {
                floatingIcon?.setImageResource(R.drawable.ic_stop)
            } else {
                floatingIcon?.setImageResource(R.drawable.ic_microphone)
            }
            cleanButton?.visibility = View.GONE
            editButton?.visibility = View.GONE
            submitButton?.visibility = View.GONE
        }
    }

    private fun resetButtonColor() {
        floatView?.findViewById<CardView>(R.id.floating_red_button)?.setCardBackgroundColor(
            Color.parseColor("#66000000")
        )
    }

    private fun captureAndUpload() {
        isProcessing = true
        val taskId = currentTaskId.incrementAndGet()
        Log.d(TAG, "Starting task #$taskId")
        updateFloatingText()
        lastTapPoints.clear()
        serviceScope.launch {
            try {
                if (currentTaskId.get() != taskId) {
                    Log.d(TAG, "Task #$taskId cancelled before start")
                    return@launch
                }
                val a11yService = TapAccessibilityService.Companion.instance
                if (a11yService == null) {
                    Log.e(TAG, "TapAccessibilityService instance is null")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FloatingWindowService,
                            "无障碍服务未连接",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    isProcessing = false
                    return@launch
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Log.e(TAG, "takeScreenshot requires API 34+")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FloatingWindowService,
                            "当前系统不支持无障碍截图(需Android 14+)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    isProcessing = false
                    return@launch
                }
                /*withContext(Dispatchers.Main) {
                    redButton?.visibility = View.GONE
                }
                delay(100)
                //获取元素
                getAllClickableNodes()
                delay(100)
                withContext(Dispatchers.Main) {
                    redButton?.visibility = View.VISIBLE
                }*/
                withContext(Dispatchers.Main) {
                    redButton?.visibility = View.GONE
                }
                delay(100)

                val bitmap = a11yService.captureScreenshot()
                Log.d(TAG, "screenshot captured: ${bitmap.width}x${bitmap.height}")

                val recognizedTextResult = recognizeTextFromBitmap(bitmap)
                Log.d(TAG, "text recognition result: $recognizedTextResult")

                sendImageRecognitionResult(recognizedTextResult)

                withContext(Dispatchers.Main) {
                    redButton?.visibility = View.VISIBLE
                }
                var imageUrl: String = ""
                if(isEnableImage){
                    val file = saveBitmapToFile(bitmap)
                    Log.d(TAG, "screenshot saved to: ${file.absolutePath}")

                    imageUrl = uploadScreenshot(file)
                    Log.d(TAG, "upload success, url: $imageUrl")

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FloatingWindowService,
                            "上传成功: $imageUrl",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }


                sendToLlm(taskId, imageUrl, recognizedText,recognizedTextResult)

                mainHandler.post {
                    recognizedText = ""
                    updateFloatingText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureAndUpload failed: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@FloatingWindowService,
                        "截图上传失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                isProcessing = false
            }
        }
    }

    fun getAllClickableNodes() {
        val a11yService = TapAccessibilityService.Companion.instance
        if (a11yService == null) {
            Log.e(TAG, "getAllClickableNodes: Accessibility service not available")
            showToastOnMainThread("无障碍服务未开启")
            return
        }

        val rootNode = a11yService.rootInActiveWindow
        if (rootNode == null) {
            Log.e(TAG, "getAllClickableNodes: Root node is null - check if accessibility service is properly connected")
            showToastOnMainThread("无法获取当前窗口信息")
            return
        }

        val clickableNodes = mutableListOf<NodeInfo>()
        collectClickableNodes(rootNode, clickableNodes)

        if (clickableNodes.isEmpty()) {
            Log.d(TAG, "getAllClickableNodes: No clickable nodes found")
            return
        }

        Log.d(TAG, "========== Clickable Nodes Found (${clickableNodes.size}) ==========")
        clickableNodes.forEachIndexed { index, node ->
            Log.d(TAG, "Node ${index + 1}:")
            Log.d(TAG, "  Text: ${node.text ?: "N/A"}")
            Log.d(TAG, "  ContentDescription: ${node.contentDescription ?: "N/A"}")
            Log.d(TAG, "  ClassName: ${node.className ?: "N/A"}")
            Log.d(TAG, "  Bounds: ${node.bounds}")
            Log.d(TAG, "  Center X: ${node.centerX}, Center Y: ${node.centerY}")
        }
        Log.d(TAG, "============================================================")
    }

    private fun showToastOnMainThread(message: String) {
        mainHandler.post {
            Toast.makeText(this@FloatingWindowService, message, Toast.LENGTH_SHORT).show()
        }
    }

    private data class NodeInfo(
        val text: CharSequence?,
        val contentDescription: CharSequence?,
        val className: CharSequence?,
        val bounds: Rect,
        val centerX: Float,
        val centerY: Float
    )

    private fun collectClickableNodes(node: AccessibilityNodeInfo, result: MutableList<NodeInfo>) {
        try {
            // 检查节点是否可点击
            if (node.isClickable || node.isFocusable || node.isLongClickable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                // 过滤过小的节点（可能是图标）
                if (bounds.width() >= 20 && bounds.height() >= 20) {
                    val centerX = bounds.left + (bounds.width() / 2f)
                    val centerY = bounds.top + (bounds.height() / 2f)

                    result.add(
                        NodeInfo(
                            text = node.text,
                            contentDescription = node.contentDescription,
                            className = node.className,
                            bounds = bounds,
                            centerX = centerX,
                            centerY = centerY
                        )
                    )
                }
            }

            // 递归遍历子节点
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectClickableNodes(child, result)
                    child.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting clickable nodes: ${e.message}", e)
        }
    }

    private fun cancelProcessing() {
        currentTaskId.incrementAndGet()
        isProcessing = false
        mainHandler.post {
            updateFloatingText()
        }
        Toast.makeText(this@FloatingWindowService, "任务已取消", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "Processing cancelled, new taskId=$currentTaskId")
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun sendToLlm(taskId: Long, imageUrl: String?, question: String, content: String?) {
        serviceScope.launch {
            if((content == null || content == "") && (imageUrl == "" || imageUrl == null)){
                return@launch
            }
            val history = mutableListOf<ProcessHistory>()
            var currentImageUrl = imageUrl
            var currentContent = content
            var loopCount = 0

            while (loopCount < maxLoops) {
                loopCount++
                Log.d(TAG, "=== LLM循环第${loopCount}轮 taskId=$taskId ===")

                if (currentTaskId.get() != taskId) {
                    Log.d(TAG, "Task #$taskId cancelled, exiting loop")
                    break
                }

                val lLmResponseExample = LLmResponse(
                    listOf(TapPoints(1.0, 10.0, 100)),
                    "用户指令",
                    false,
                    "简短的执行细节"
                )

                val gson = Gson()
                val json = gson.toJson(lLmResponseExample)
                val llmPrompt =
                    if(isEnableImage)
                        buildLlmPrompt(this@FloatingWindowService, json)
                    else
                        buildLlmPrompt(this@FloatingWindowService, json)
                val llmBody = LLmBody(llmPrompt, question, screenWidth, screenHeight, history, "")

                try {
                    val startTime = System.currentTimeMillis()
                    // 从数据库获取活跃模型配置
                    val activeModel = MyApp.Companion.repository.getActiveModelSync()
                    val apiKey = activeModel?.apiKey ?: getString(R.string.llm_api_key)
                    val model = activeModel?.modelName ?: getString(R.string.llm_model)
                    val modelId = activeModel?.id ?: 0L
                    val baseUrl = activeModel?.baseUrl ?: getString(R.string.llm_base_url)
                    var request: LlmRequest;
                    if(isEnableImage && currentImageUrl != null){
                        request = LlmRequest(
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
                            ),
                            thinking = LLmThinkingType("disabled")
                        )
                    }else{
                        llmBody.z_content = currentContent
                        request = LlmRequest(
                            model = model,
                            messages = listOf(
                                LlmMessage(
                                    role = "user",
                                    content = listOf(
                                        LlmContent(
                                            type = "text",
                                            text = gson.toJson(llmBody)
                                        )
                                    )
                                )
                            ),
                            thinking = LLmThinkingType("disabled")
                        )
                    }


                    val llmApi = RetrofitClient.getLlmApi(baseUrl)
                    val response = llmApi.chatCompletion(
                        url = baseUrl,
                        auth = "Bearer $apiKey",
                        request = request
                    )

                    val answer = response.choices.firstOrNull()?.message?.content ?: "返回：null"
                    Log.d(TAG, "LLM response: $answer")
                    val llmResponse = gson.fromJson(answer, LLmResponse::class.java)

                    val inputToken = response.usage?.prompt_tokens ?: 0
                    val outputToken = response.usage?.completion_tokens ?: 0
                    val answerTime = System.currentTimeMillis() - startTime

                    // 记录对话到数据库
                    serviceScope.launch {
                        try {
                            MyApp.Companion.repository.insertConversation(
                                remark = llmResponse?.remark ?: answer,
                                inputToken = inputToken,
                                outputToken = outputToken,
                                answerTime = answerTime,
                                modelId = modelId,
                                modelName = model
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "记录对话失败: ${e.message}", e)
                        }
                    }
                    if (currentTaskId.get() == taskId) {
                        llmResponse?.tapPoints?.forEach { tapPoint ->
                            Log.d(TAG, "执行点击: x=${tapPoint.tapX}, y=${tapPoint.tapY}, delay=${tapPoint.delay}")
                            // 将点击坐标添加到列表中
                            lastTapPoints.add(tapPoint)
                            TapAccessibilityService.Companion.instance?.simulateTap(
                                tapPoint.tapX.toFloat(),
                                tapPoint.tapY.toFloat(),
                                tapPoint.delay.toLong(),
                                1
                            )
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@FloatingWindowService,
                                "LLM: ${llmResponse?.remark ?: answer}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        sendLlmResultToActivity(llmResponse.remark ?: answer)
                        if (llmResponse?.isEnd == true) {
                            Log.d(TAG, "任务完成")
                            isProcessing = false
                            mainHandler.post {
                                updateFloatingText()
                            }
                            break
                        }

                        llmResponse?.remark?.let {

                            history.add(ProcessHistory(it, llmResponse.tapPoints))
                        }

                        if (loopCount < maxLoops) {
                            delay(1500)
                            withContext(Dispatchers.Main) {
                                redButton?.visibility = View.GONE
                            }
                            delay(100)

                            val bitmap = TapAccessibilityService.Companion.instance?.captureScreenshot()

                            if(isEnableImage){
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
                            }else{
                                if (bitmap != null) {
                                    currentContent = recognizeTextFromBitmap(bitmap)
                                }

                            }
                            withContext(Dispatchers.Main) {
                                redButton?.visibility = View.VISIBLE
                            }

                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "sendToLlm task #$taskId cancelled")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "sendToLlm第${loopCount}轮失败: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FloatingWindowService,
                            "LLM请求失败: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    break
                }
            }

            if (loopCount >= maxLoops && currentTaskId.get() == taskId) {
                Log.w(TAG, "达到最大循环次数$maxLoops，强制结束")
                sendLlmResultToActivity("任务执行达到最大循环次数")
            }

            if (currentTaskId.get() == taskId) {
                isProcessing = false
                mainHandler.post {
                    updateFloatingText()
                }
            }
        }
    }

    private fun sendLlmResultToActivity(result: String) {
        val intent = Intent(ACTION_LLM_RESULT)
        intent.putExtra(EXTRA_LLM_RESULT, result)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendVoiceRecognitionResult(text: String) {
        val intent = Intent(ACTION_VOICE_RECOGNITION)
        intent.putExtra(EXTRA_VOICE_TEXT, text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private fun sendImageRecognitionResult(text: String) {
        val intent = Intent(ACTION_IMAGE_RECOGNITION)
        intent.putExtra(EXTRA_IMAGE_TEXT, text)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    private suspend fun recognizeTextFromBitmap(bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始识别，bitmap尺寸: ${bitmap.width}x${bitmap.height}")

                val image = InputImage.fromBitmap(bitmap, 0)
                // 第一次：英文识别器
                Log.d(TAG, "使用英文识别器识别")
                val latinRecognizer =
                    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val latinResult = suspendCancellableCoroutine<Text?> { continuation ->
                    latinRecognizer.process(image)
                        .addOnSuccessListener { continuation.resume(it) {} }
                        .addOnFailureListener { continuation.resume(null) {} }
                }

                // 第二次：中文识别器
                Log.d(TAG, "使用中文识别器识别")
                val chineseOptions = ChineseTextRecognizerOptions.Builder().build()
                val chineseRecognizer = TextRecognition.getClient(chineseOptions)
                val chineseResult = suspendCancellableCoroutine<Text?> { continuation ->
                    chineseRecognizer.process(image)
                        .addOnSuccessListener { continuation.resume(it) {} }
                        .addOnFailureListener { continuation.resume(null) {} }
                }

                // 合并结果去重
                val recognizedTexts = mutableSetOf<String>()
                val allBlocks = mutableListOf<Pair<Text.TextBlock, String>>()

                if(isEnableEnglish){
                    // 添加英文识别结果
                    latinResult?.let {
                        for (block in it.textBlocks) {
                            if (!block.text.isBlank()) {
                                recognizedTexts.add(block.text)
                                allBlocks.add(Pair(block, "英文"))
                            }
                        }
                    }
                }
                // 添加中文识别结果（去重）
                chineseResult?.let {
                    for (block in it.textBlocks) {
                        if (!block.text.isBlank() && !recognizedTexts.contains(block.text)) {
                            recognizedTexts.add(block.text)
                            allBlocks.add(Pair(block, "中文"))
                        }
                    }
                }

                // 解析结果，包含坐标信息
                if (recognizedTexts.isEmpty()) {
                    "未识别到任何文本（图片可能没有清晰文字）"
                } else {
                    val stringBuilder = StringBuilder()

                    for ((index, pair) in allBlocks.withIndex()) {
                        val block = pair.first
                        val source = pair.second
                        val blockRect = block.boundingBox
                        stringBuilder.append("context: \"${block.text}\"")
                        if (blockRect != null) {
                            stringBuilder.append("坐标:(${blockRect.centerX()},${blockRect.centerY()})")
                            //stringBuilder.append("  边界: 左=${blockRect.left}, 上=${blockRect.top}, 右=${blockRect.right}, 下=${blockRect.bottom}\n")
                        }

                        /*                       // 输出每行信息
                                               for (line in block.lines) {
                                                   val lineRect = line.boundingBox
                                                   stringBuilder.append("    行: \"${line.text}\"\n")
                                                   if (lineRect != null) {
                                                       stringBuilder.append("      中心: (${lineRect.centerX()}, ${lineRect.centerY()})\n")
                                                   }
                                               }*/
                    }

                    stringBuilder.toString()
                }

            } catch (e: Exception) {
                Log.e(TAG, "识别异常: ${e.message}", e)
                when (e) {
                    is MlKitException -> {
                        when (e.errorCode) {
                            MlKitException.NOT_FOUND -> "模型未下载完成"
                            else -> "ML Kit错误: ${e.message}"
                        }
                    }

                    else -> "识别失败: ${e.message}"
                }
            }
        }
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
        return BitmapFactory.decodeStream(bais)!!
    }

    private fun addGridAndCoordinates(bitmap: Bitmap, origWidth: Int, origHeight: Int, tapPoints: List<TapPoints> = emptyList()): Bitmap {
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

        // 绘制所有点击坐标
        tapPoints.forEachIndexed { index, tapPoint ->
            val tapPx = tapPoint.tapX.toFloat() * scaleX
            val tapPy = tapPoint.tapY.toFloat() * scaleY

            // 绘制点击点（红色半透明圆圈）
            paint.color = Color.parseColor("#88FF0000")
            paint.style = Paint.Style.FILL
            canvas.drawCircle(tapPx, tapPy, 15f, paint)

            // 绘制点击序号
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.style = Paint.Style.FILL
            val textWidth = paint.measureText("${index + 1}")
            canvas.drawText("${index + 1}", tapPx - textWidth / 2, tapPy + 5, paint)
        }

        return mutableBitmap
    }

    private suspend fun saveBitmapToFile(bitmap: Bitmap): File {
        val origWidth = bitmap.width
        val origHeight = bitmap.height
        val scaledBitmap = scaleDown(bitmap)
        val compressedBitmap = compressToLowQuality(scaledBitmap)
        if (scaledBitmap !== bitmap) scaledBitmap.recycle()
        val bitmapWithGrid = addGridAndCoordinates(compressedBitmap, origWidth, origHeight, lastTapPoints)
        compressedBitmap.recycle()
        val dir = cacheDir
        val fileName = getImagePath()
        val file = File(dir, fileName)
        FileOutputStream(file).use { fos ->
            bitmapWithGrid.compress(Bitmap.CompressFormat.PNG, 100, fos)
        }
        bitmapWithGrid.recycle()
        bitmap.recycle()
        return file
    }

    private suspend fun getImagePath(): String {
        cachedImagePath?.let { return it }
        val saved = prefs.getString("image_path", null)
        if (saved != null) {
            cachedImagePath = saved
            return saved
        }
        return try {
            val response = RetrofitClient.uploadApi.getPath()
            if (response.code == 1 && response.data != null) {
                prefs.edit().putString("image_path", response.data+".png").apply()
                cachedImagePath = response.data+".png"
                Log.d(TAG, "getImagePath from API: ${response.data}")
                response.data+".png"
            } else {
                Log.d(TAG, "getImagePath from API: ${response.data}")
                "smartFloat.png"
            }
        } catch (e: Exception) {
            Log.e(TAG, "getImagePath failed: ${e.message}", e)
            "smartFloat.png"
        }
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