package com.zyy.smartfloat.floatbut

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.R
import com.zyy.smartfloat.database.AppDatabase
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
import com.zyy.smartfloat.prompt.buildLlmPrompt_image
import com.zyy.smartfloat.service.FloatingWindowService
import com.zyy.smartfloat.service.TapAccessibilityService
import com.zyy.smartfloat.utils.VoiceRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

/**
 * 悬浮窗业务处理器：从 FloatingWindowService 抽取出来的非 UI 业务逻辑。
 *
 * 职责：设置读取、任务状态管理、语音识别编排、截图+OCR、
 * 图片处理与上传、LLM 多轮循环执行、Token 阈值提醒、广播通知。
 *
 * 悬浮窗视图、交互与通知仍由 FloatingWindowService 负责，
 * 两者通过 [UiCallback] 回调（实现内部需保证主线程执行）与公开状态协作。
 */
class FloatTaskProcessor(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val uiCallback: UiCallback
) {

    /** Service 需要响应的业务回调 */
    interface UiCallback {
        /** 刷新悬浮窗文本/图标状态 */
        fun onUpdateFloatingText()

        /** 红按钮显示/隐藏 */
        fun onRedButtonVisibility(visible: Boolean)

        /** 设置红按钮背景色 */
        fun onRedButtonColor(color: String)

        /** 弹 Toast，long 为 true 时使用 LENGTH_LONG */
        fun showToast(message: String, long: Boolean)
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { context.getSharedPreferences("SmartFloatSettings", Context.MODE_PRIVATE) }

    companion object {
        const val BUTTON_COLOR_IDLE = "#66000000"    // 红按钮常态背景
        const val BUTTON_COLOR_RECORDING = "#E53935" // 录音中背景
        private const val TAG = "FloatTaskProcessor"
    }

    // ===== 设置 =====
    var isEnableImage: Boolean = false
        private set
    var isEnableEnglish: Boolean = false
        private set
    var maxLoops: Int = 100
        private set
    var tokenThreshold: Int = 100000
        private set

    // ===== 语音识别器实例 =====
    private var voiceRecognizer: VoiceRecognizer? = null

    // ===== 任务状态 =====
    private val currentTaskId = AtomicLong(0)
    var isProcessing: Boolean = false
        private set
    // 识别/用户输入的指令文本，Service 在编辑模式退出时会回写
    var recognizedText: String = ""
    var isRecording: Boolean = false
        private set
    var longPressTriggered: Boolean = false
    private var lastTapPoints = mutableListOf<TapPoints>()
    private var cachedImagePath: String? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private val MAX_HISTORY_SIZE = 3 // history最大长度

    fun refreshSettings() {
        isEnableImage = prefs.getBoolean("enable_image", false)
        isEnableEnglish = prefs.getBoolean("enable_english", false)
        maxLoops = prefs.getInt("max_loops", 100)
        tokenThreshold = prefs.getInt("token_threshold", 100000)
        Log.d(TAG, "Settings refreshed: isEnableImage=$isEnableImage, isEnableEnglish=$isEnableEnglish, maxLoops=$maxLoops, tokenThreshold=$tokenThreshold")
    }

    fun refreshScreenSize() {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        val display = wm.defaultDisplay
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        Log.d(TAG, "Screen width: $screenWidth, height: $screenHeight")
    }

    private fun checkAndNotifyTokenThreshold() {
        if (tokenThreshold <= 0) {
            Log.d(TAG, "Token check: todayUsage=0, threshold=$tokenThreshold")
            return
        }
        val database = AppDatabase.getInstance(context)
        val todayUsage = database.getTodayTokenUsage()
        Log.d(TAG, "Token check: todayUsage=$todayUsage, threshold=$tokenThreshold")

        if (todayUsage >= tokenThreshold) {
            val notification = Notification.Builder(context, FloatingWindowService.TOKEN_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Token消耗提醒")
                .setContentText("今日已使用 $todayUsage Tokens，已超过阈值 $tokenThreshold")
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.notify(FloatingWindowService.NOTIFICATION_ID + 1, notification)
            Log.d(TAG, "Token threshold exceeded notification sent")
        }
    }

    // ===== 语音识别 =====

    fun startRecording() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "录音权限未授予")
            return
        }

        voiceRecognizer = VoiceRecognizer(context)
        voiceRecognizer?.setCallbacks(
            onResult = { text ->
                Log.d(TAG, "语音识别结果: $text")
                recognizedText = text
                isRecording = false
                sendVoiceRecognitionResult(text)
                mainHandler.post {
                    uiCallback.onUpdateFloatingText()
                    uiCallback.onRedButtonColor(BUTTON_COLOR_IDLE)
                }
            },
            onError = { error ->
                Log.e(TAG, "语音识别失败: $error")
                isRecording = false
                sendVoiceRecognitionResult(error)
                mainHandler.post {
                    uiCallback.onRedButtonColor(BUTTON_COLOR_IDLE)
                    uiCallback.onUpdateFloatingText()
                }
            },
            onStart = {
                Log.d(TAG, "开始录音")
                isRecording = true
                longPressTriggered = true
                mainHandler.post {
                    // 改变按钮背景为红色表示录音中
                    uiCallback.onRedButtonColor(BUTTON_COLOR_RECORDING)
                }
            }
        )

        voiceRecognizer?.startRecording()
    }

    fun stopRecordingAndRecognize() {
        Log.d(TAG, "stopRecordingAndRecognize: 停止录音并识别")
        try {
            voiceRecognizer?.stopRecordingAndRecognize()
        } catch (e: Exception) {
            Log.e(TAG, "stopRecordingAndRecognize: 停止失败, error=${e.message}", e)
            isRecording = false
            uiCallback.onRedButtonColor(BUTTON_COLOR_IDLE)
        }
    }

    /** 释放语音识别资源，在 Service.onDestroy 中调用 */
    fun release() {
        voiceRecognizer?.release()
        voiceRecognizer = null
    }

    // ===== 主任务：截图+OCR+LLM 循环 =====

    fun captureAndUpload() {
        isProcessing = true
        val taskId = currentTaskId.incrementAndGet()
        Log.d(TAG, "Starting task #$taskId")
        uiCallback.onUpdateFloatingText()
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
                    uiCallback.showToast("无障碍服务未连接", false)
                    isProcessing = false
                    return@launch
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    Log.e(TAG, "takeScreenshot requires API 34+")
                    uiCallback.showToast("当前系统不支持无障碍截图(需Android 14+)", false)
                    isProcessing = false
                    return@launch
                }
                /*withContext(Dispatchers.Main) {
                    uiCallback.onRedButtonVisibility(false)
                }
                delay(100)
                //获取元素
                getAllClickableNodes()
                delay(100)
                withContext(Dispatchers.Main) {
                    uiCallback.onRedButtonVisibility(true)
                }*/
                uiCallback.onRedButtonVisibility(false)
                delay(100)

                val bitmap = a11yService.captureScreenshot()
                Log.d(TAG, "screenshot captured: ${bitmap.width}x${bitmap.height}")

                val recognizedTextResult = recognizeTextFromBitmap(bitmap)
                Log.d(TAG, "text recognition result: $recognizedTextResult")

                sendImageRecognitionResult(recognizedTextResult)

                uiCallback.onRedButtonVisibility(true)
                var imageUrl: String = ""
                if (isEnableImage) {
                    val file = saveBitmapToFile(bitmap)
                    Log.d(TAG, "screenshot saved to: ${file.absolutePath}")

                    imageUrl = uploadScreenshot(file)
                    Log.d(TAG, "upload success, url: $imageUrl")

                    uiCallback.showToast("上传成功: $imageUrl", true)
                }


                sendToLlm(taskId, imageUrl, recognizedText, recognizedTextResult)

                mainHandler.post {
                    recognizedText = ""
                    uiCallback.onUpdateFloatingText()
                }
            } catch (e: Exception) {
                Log.e(TAG, "captureAndUpload failed: ${e.message}", e)
                uiCallback.showToast("截图上传失败: ${e.message}", true)
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
            uiCallback.showToast(message, false)
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

    fun cancelProcessing() {
        currentTaskId.incrementAndGet()
        isProcessing = false
        uiCallback.onUpdateFloatingText()
        uiCallback.showToast("任务已取消", false)
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
                        buildLlmPrompt_image(context, json)
                    else
                        buildLlmPrompt(context, json)
                val activeSkills = MyApp.Companion.repository.getActiveOutputSkillSync()
                val llmBody:LLmBody;
                if(activeSkills.skillsName != ""){
                    llmBody = LLmBody(llmPrompt, question, activeSkills,screenWidth, screenHeight, history, "")
                }else{
                    llmBody = LLmBody(a_prompt = llmPrompt, b_question = question, c_maxX = screenWidth, c_maxY = screenHeight, d_history = history, z_content = "")
                }

                try {
                    val startTime = System.currentTimeMillis()
                    // 从数据库获取活跃模型配置
                    val activeModel = MyApp.Companion.repository.getActiveModelSync()
                    val apiKey = activeModel?.apiKey ?: context.getString(R.string.llm_api_key)
                    val model = activeModel?.modelName ?: context.getString(R.string.llm_model)
                    val modelId = activeModel?.id ?: 0L
                    val baseUrl = activeModel?.baseUrl ?: context.getString(R.string.llm_base_url)
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
                    // 处理部分API对content的双重JSON编码：answer可能以"开头
                    val cleanAnswer = if (answer.trimStart().startsWith("\"")) {
                        gson.fromJson(answer, String::class.java) ?: answer
                    } else {
                        answer
                    }
                    val llmResponse = gson.fromJson(cleanAnswer, LLmResponse::class.java)

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
                            checkAndNotifyTokenThreshold()
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
                        uiCallback.showToast("LLM: ${llmResponse?.remark ?: answer}", false)
                        sendLlmResultToActivity(llmResponse.remark ?: answer)
                        if (llmResponse?.isEnd == true) {
                            Log.d(TAG, "任务完成")
                            isProcessing = false
                            uiCallback.onUpdateFloatingText()
                            break
                        }

                        llmResponse?.remark?.let {
                            if (history.size >= MAX_HISTORY_SIZE) {
                                history.removeAt(0)
                            }
                            history.add(ProcessHistory(it, llmResponse.tapPoints))
                        }

                        if (loopCount < maxLoops) {
                            delay(1500)
                            uiCallback.onRedButtonVisibility(false)
                            delay(100)

                            val bitmap = TapAccessibilityService.Companion.instance?.captureScreenshot()

                            if(isEnableImage){
                                uiCallback.onRedButtonVisibility(true)

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
                            uiCallback.onRedButtonVisibility(true)

                        }
                    }
                } catch (e: CancellationException) {
                    Log.d(TAG, "sendToLlm task #$taskId cancelled")
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "sendToLlm第${loopCount}轮失败: ${e.message}", e)
                    uiCallback.showToast("LLM请求失败: ${e.message}", true)
                    break
                }
            }

            if (loopCount >= maxLoops && currentTaskId.get() == taskId) {
                Log.w(TAG, "达到最大循环次数$maxLoops，强制结束")
                sendLlmResultToActivity("任务执行达到最大循环次数")
            }

            if (currentTaskId.get() == taskId) {
                isProcessing = false
                uiCallback.onUpdateFloatingText()
            }
        }
    }

    // ===== 广播通知 =====

    private fun sendLlmResultToActivity(result: String) {
        val intent = Intent(FloatingWindowService.ACTION_LLM_RESULT)
        intent.putExtra(FloatingWindowService.EXTRA_LLM_RESULT, result)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun sendVoiceRecognitionResult(text: String) {
        val intent = Intent(FloatingWindowService.ACTION_VOICE_RECOGNITION)
        intent.putExtra(FloatingWindowService.EXTRA_VOICE_TEXT, text)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun sendImageRecognitionResult(text: String) {
        val intent = Intent(FloatingWindowService.ACTION_IMAGE_RECOGNITION)
        intent.putExtra(FloatingWindowService.EXTRA_IMAGE_TEXT, text)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    // ===== OCR =====

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

    // ===== 图片处理与上传 =====

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
        val dir = context.cacheDir
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
}
