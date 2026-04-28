package com.zyy.smartfloat

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        Log.d(TAG, "onServiceConnected, isRunning=$isRunning")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
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
}
