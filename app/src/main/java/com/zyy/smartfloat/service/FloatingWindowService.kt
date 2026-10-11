package com.zyy.smartfloat.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
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
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.zyy.smartfloat.NavActivity
import com.zyy.smartfloat.R
import com.zyy.smartfloat.floatbut.FloatTaskProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 悬浮窗前台 Service：只负责悬浮窗视图、触摸交互、通知与生命周期。
 * 业务逻辑（语音识别、截图+OCR、图片上传、LLM 循环、Token 阈值提醒、
 * 广播通知）已抽取到 com.zyy.smartfloat.floatbut.FloatTaskProcessor。
 */
class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var floatView: View? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isShowing = false
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val LONG_PRESS_THRESHOLD = 500L // 长按阈值500ms
    private val DOUBLE_CLICK_INTERVAL = 300L // 双击间隔阈值
    private var lastClickTime = 0L // 上次点击时刻（eventTime，用于双击检测）

    // 业务处理器：承接语音/截图OCR/图片上传/LLM循环/广播通知
    private lateinit var processor: FloatTaskProcessor

    private var floatingText: EditText? = null
    private var floatingIcon: ImageView? = null
    private var cleanButton: ImageView? = null
    private var editButton: ImageView? = null
    private var submitButton: ImageView? = null
    private var isEditing = false
    private var redButton: CardView? = null
    private var floatLayoutParams: WindowManager.LayoutParams? = null

    // 用于接收从 Intent 传递的问题
    private var llmQuestion: String = ""

    companion object {
        const val CHANNEL_ID = "floating_window_channel"
        const val TOKEN_CHANNEL_ID = "token_reminder_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.zyy.smartfloat.STOP_SERVICE"
        const val ACTION_LLM_RESULT = "com.zyy.smartfloat.LLM_RESULT"
        const val ACTION_TASK_START = "com.zyy.smartfloat.TASK_START"
        const val ACTION_VOICE_RECOGNITION = "com.zyy.smartfloat.VOICE_RECOGNITION"
        const val ACTION_IMAGE_RECOGNITION = "com.zyy.smartfloat.IMAGE_RECOGNITION"
        const val ACTION_FLOATING_STOPPED = "com.zyy.smartfloat.FLOATING_STOPPED"
        const val EXTRA_LLM_RESULT = "extra_llm_result"
        const val EXTRA_LLM_QUESTION = "extra_llm_question"
        const val EXTRA_VOICE_TEXT = "extra_voice_text"
        const val EXTRA_IMAGE_TEXT = "extra_image_text"

        // 服务是否正在运行，供 UI 层初始化悬浮窗状态
        @Volatile
        var isServiceRunning = false

        private const val TAG = "FloatingWindowService"
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        processor = FloatTaskProcessor(this, serviceScope, object : FloatTaskProcessor.UiCallback {
            override fun onUpdateFloatingText() {
                mainHandler.post { updateFloatingText() }
            }

            override fun onRedButtonVisibility(visible: Boolean) {
                mainHandler.post { redButton?.visibility = if (visible) View.VISIBLE else View.GONE }
            }

            override fun onRedButtonColor(color: String) {
                mainHandler.post {
                    floatView?.findViewById<CardView>(R.id.floating_red_button)?.setCardBackgroundColor(
                        Color.parseColor(color)
                    )
                }
            }

            override fun showToast(message: String, long: Boolean) {
                mainHandler.post {
                    Toast.makeText(
                        this@FloatingWindowService,
                        message,
                        if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
        createNotificationChannel()
        processor.refreshScreenSize()
        processor.refreshSettings()
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
        isServiceRunning = false
        serviceScope.cancel()
        hideFloatingWindow()
        mainHandler.removeCallbacks(longPressRunnable)
        processor.release()
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

            val tokenChannel = NotificationChannel(
                TOKEN_CHANNEL_ID,
                "Token消耗提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "当今日Token消耗超过阈值时发送提醒"
            }
            manager.createNotificationChannel(tokenChannel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, NavActivity::class.java),
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
                    processor.recognizedText = floatingText?.text?.toString() ?: ""
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(floatingText?.windowToken, 0)
                    updateFloatingText()
                    editButton?.setImageResource(R.drawable.ic_edit)
                }
            }

            cleanButton?.setOnClickListener {
                // 清空文本
                floatingText?.text = null
                processor.recognizedText = ""
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
                if (processor.recognizedText.isNotEmpty()) {
                    // 取消焦点和编辑模式
                    floatingText?.clearFocus()
                    isEditing = false
                    processor.refreshSettings()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(floatingText?.windowToken, 0)
                    processor.recognizedText = floatingText?.text?.toString() ?: ""
                    Toast.makeText(this@FloatingWindowService, "处理中...", Toast.LENGTH_SHORT).show()
                    processor.captureAndUpload()
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
                        processor.longPressTriggered = false
                        // 如果正在处理中，不启动长按检测，准备处理点击取消
                        if (!processor.isProcessing) {
                            mainHandler.postDelayed(longPressRunnable, LONG_PRESS_THRESHOLD)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        Log.d(TAG, "button is MOVE ("+event.rawX+" , "+event.rawY+")")
                        wm.updateViewLayout(floatView, layoutParams)

                        // 如果移动距离超过阈值，取消长按检测（防止移动时触发录音）
                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        if (deltaX > 10 || deltaY > 10) {
                            mainHandler.removeCallbacks(longPressRunnable)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        mainHandler.removeCallbacks(longPressRunnable)

                        val deltaX = Math.abs(event.rawX - initialTouchX)
                        val deltaY = Math.abs(event.rawY - initialTouchY)

                        // 只有当移动距离小于阈值时才认为是点击操作
                        if (deltaX <= 10 && deltaY <= 10) {
                            if (processor.isProcessing) {
                                // 如果正在处理中，点击按钮取消任务
                                processor.cancelProcessing()
                            } else if (!processor.isRecording) {
                                // 非录音状态：双击关闭悬浮窗
                                if (event.eventTime - lastClickTime < DOUBLE_CLICK_INTERVAL) {
                                    lastClickTime = 0
                                    stopService()
                                } else {
                                    lastClickTime = event.eventTime
                                }
                            }
                        }
                        if (processor.longPressTriggered && processor.isRecording) {
                            // 停止录音并识别
                            processor.stopRecordingAndRecognize()
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
        if (!processor.isRecording && processor.recognizedText.isEmpty() && !processor.isProcessing) {
            processor.startRecording()
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
        } else if (processor.recognizedText.isNotEmpty()) {
            // 有文本但不在编辑模式
            floatingText?.setText(processor.recognizedText)
            floatingText?.visibility = View.VISIBLE
            floatingIcon?.visibility = View.GONE
            cleanButton?.visibility = View.VISIBLE
            editButton?.visibility = View.VISIBLE
            submitButton?.visibility = View.VISIBLE
        } else {
            // 无文本且不在编辑模式
            floatingText?.visibility = View.GONE
            floatingIcon?.visibility = View.VISIBLE
            if (processor.isProcessing) {
                floatingIcon?.setImageResource(R.drawable.ic_stop)
            } else {
                floatingIcon?.setImageResource(R.drawable.ic_microphone)
            }
            cleanButton?.visibility = View.GONE
            editButton?.visibility = View.GONE
            submitButton?.visibility = View.GONE
        }
    }

    private fun hideFloatingWindow() {
        try {
            if (isShowing && floatView != null) {
                windowManager?.removeView(floatView)
                isShowing = false
                floatView = null
                // 通知 UI 层悬浮窗已关闭（双击红按钮/通知"隐藏"/服务销毁均会走到这里）
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                    Intent(ACTION_FLOATING_STOPPED)
                )
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
