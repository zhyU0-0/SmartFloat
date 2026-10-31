package com.zyy.smartfloat.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import com.zyy.smartfloat.database.SFTouchEvent
import com.zyy.smartfloat.database.SFTouchEventComposition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TapAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TapAccessibilityService"

        @Volatile
        var instance: TapAccessibilityService? = null
            private set

        @Volatile
        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gestureScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 组合是否正在执行，避免重复触发
    @Volatile
    private var isExecutingComposition = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.d(TAG, "onServiceConnected, isRunning=$isRunning")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        gestureScope.cancel()
        isExecutingComposition = false
        instance = null
        isRunning = false
        super.onDestroy()
    }

    fun simulateTap(x: Float, y: Float, delayMs: Long, tapCount: Int) {
        Log.d(TAG, "simulateTap: ($x, $y), delay=$delayMs, count=$tapCount")
        handler.postDelayed({
            for (i in 1..tapCount) {
                val clickPath = Path().apply {
                    moveTo(x, y)
                }

                val clickStroke = GestureDescription.StrokeDescription(
                    clickPath,
                    0,
                    1
                )

                val clickGesture = GestureDescription.Builder()
                    .addStroke(clickStroke)
                    .build()

                val dispatched = dispatchGesture(clickGesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "tap completed at ($x, $y)")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "tap cancelled at ($x, $y)")
                    }
                }, null)

                Log.d(TAG, "dispatchGesture returned: $dispatched for tap #$i at ($x, $y)")
            }
        }, delayMs)
    }

    /**
     * 按顺序执行一个组合里的事件：延时 / 点击 / 滑动。
     * 坐标与设计平台记录的一致，均为屏幕像素坐标。
     */
    fun executeComposition(composition: SFTouchEventComposition) {
        if (isExecutingComposition) {
            Log.d(TAG, "executeComposition ignored: composition already running")
            return
        }
        if (composition.composition.isEmpty()) {
            Log.d(TAG, "executeComposition ignored: empty composition")
            return
        }
        isExecutingComposition = true
        gestureScope.launch {
            try {
                for (event in composition.composition) {
                    executeEvent(event)
                }
                Log.d(TAG, "executeComposition finished: ${composition.name}")
            } catch (e: Exception) {
                Log.e(TAG, "executeComposition failed: ${e.message}", e)
            } finally {
                isExecutingComposition = false
            }
        }
    }

    private suspend fun executeEvent(event: SFTouchEvent) {
        when (event.type) {
            0 -> {
                //延时
                Log.d(TAG, "delay ${event.delay}ms")
                delay(event.delay.coerceAtLeast(0L))
            }
            1 -> {
                //点击
                val path = Path().apply {
                    moveTo(event.downPosition.x.toFloat(), event.downPosition.y.toFloat())
                }
                Log.d(TAG, "tap at (${event.downPosition.x}, ${event.downPosition.y})")
                dispatchPath(path, 1L)
            }
            2 -> {
                //滑动：down -> 中间采样点 -> up，时长按采样点数量估算
                val path = Path().apply {
                    moveTo(event.downPosition.x.toFloat(), event.downPosition.y.toFloat())
                    event.movePositionList.forEach {
                        lineTo(it.x.toFloat(), it.y.toFloat())
                    }
                    lineTo(event.upPosition.x.toFloat(), event.upPosition.y.toFloat())
                }
                val duration = ((event.movePositionList.size + 1) * 16L).coerceIn(50L, 800L)
                Log.d(TAG, "swipe from (${event.downPosition.x}, ${event.downPosition.y}) " +
                        "to (${event.upPosition.x}, ${event.upPosition.y}), duration=$duration")
                dispatchPath(path, duration)
            }
        }
    }

    private suspend fun dispatchPath(path: Path, durationMs: Long): Boolean =
        suspendCancellableCoroutine { continuation ->
            val stroke = GestureDescription.StrokeDescription(
                path, 0, durationMs.coerceAtLeast(1L)
            )
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            }, handler)
            if (!dispatched && continuation.isActive) {
                continuation.resume(false)
            }
        }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    suspend fun captureScreenshot(): Bitmap = suspendCancellableCoroutine { continuation ->
        val executor = Executor { runnable -> handler.post(runnable) }
        try {
            takeScreenshot(
                0,
                executor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: ScreenshotResult) {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshotResult.hardwareBuffer,
                            screenshotResult.colorSpace
                        ) ?: run {
                            screenshotResult.hardwareBuffer.close()
                            continuation.resumeWithException(RuntimeException("Bitmap.wrapHardwareBuffer returned null"))
                            return
                        }
                        continuation.resume(bitmap)
                        screenshotResult.hardwareBuffer.close()
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "takeScreenshot failed with errorCode: $errorCode")
                        continuation.resumeWithException(RuntimeException("takeScreenshot failed: errorCode=$errorCode"))
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "takeScreenshot exception: ${e.message}", e)
            continuation.resumeWithException(e)
        }
    }
}