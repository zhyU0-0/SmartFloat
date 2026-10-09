package com.zyy.smartfloat.unity.flagment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.unity3d.player.UnityPlayer
import com.zyy.smartfloat.unity.viewModel.UnityViewModel
import kotlin.collections.get

/**
 * A simple [Fragment] subclass.
 * Use the [MyUnityFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class MyUnityFragment : Fragment() {
    private var unityPlayer: UnityPlayer? = null
    private lateinit var unityViewModel: UnityViewModel

    // 手势起点（保留你原有的逻辑）
    private var downX = 0f
    private var downY = 0f

    // ---- Handler 定时采样相关 ----
    private val sampleHandler = Handler(Looper.getMainLooper())
    private var isSampling = false
    private val sampleIntervalMs = 50L

    // 当前手指位置（主线程读写，无需 volatile）
    private var currentX = 0f
    private var currentY = 0f
    // 上一次采样位置
    private var lastSampleX = 0f
    private var lastSampleY = 0f

    private val sampleRunnable = object : Runnable {
        override fun run() {
            if (!isSampling) return

            val dx = currentX - lastSampleX
            val dy = currentY - lastSampleY

            Log.d(
                "UnitySample",
                "500ms采样: 当前($currentX, $currentY) 上次($lastSampleX, $lastSampleY) -> dx=$dx, dy=$dy"
            )

            // 如需回传 Unity / ViewModel，可在这里调用
            unityViewModel.sendRotate(dx, dy)

            lastSampleX = currentX
            lastSampleY = currentY

            // 继续下一次采样
            sampleHandler.postDelayed(this, sampleIntervalMs)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        unityPlayer = UnityPlayer(requireActivity())
        unityViewModel = ViewModelProvider(requireActivity())[UnityViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = unityPlayer?.view
        (view?.parent as? ViewGroup)?.removeView(view)
        view?.viewTreeObserver?.addOnWindowFocusChangeListener { hasFocus ->
            unityPlayer?.windowFocusChanged(hasFocus)
        }

        view?.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    currentX = event.x
                    currentY = event.y
                    Log.d("UnityTouch", "按下: x=${event.x}, y=${event.y}")
                    startSampling()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    //Log.d("UnityTouch", "移动: x=${event.x}, y=${event.y}, 移动量: dx=$dx, dy=$dy")
                    //unityViewModel.sendRotate(dx, dy)

                    // 只刷新当前坐标，Handler 会按 500ms 节奏取
                    currentX = event.x
                    currentY = event.y
                }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    Log.d("UnityTouch", "抬起: x=${event.x}, y=${event.y}, 总移动: dx=$dx, dy=$dy")
                    unityViewModel.sendSnap()
                    stopSampling()
                }
                MotionEvent.ACTION_CANCEL -> {
                    Log.d("UnityTouch", "取消")
                    stopSampling()
                }
            }
            true
        }

        return view
    }

    // ---------------- Handler 采样控制 ----------------

    private fun startSampling() {
        if (isSampling) return
        isSampling = true

        // 起点作为第一次采样的基准
        lastSampleX = currentX
        lastSampleY = currentY

        // 延迟 500ms 后第一次采样
        sampleHandler.postDelayed(sampleRunnable, sampleIntervalMs)
    }

    private fun stopSampling() {
        isSampling = false
        sampleHandler.removeCallbacks(sampleRunnable)
    }

    override fun onResume() {
        super.onResume()
        unityPlayer?.resume()
    }

    override fun onPause() {
        super.onPause()
        unityPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSampling()               // 防止 Fragment 销毁后 handler 还在跑
        unityPlayer?.quit()
    }
}
