package com.zyy.smartfloat

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.tencent.cloud.qcloudasrsdk.onesentence.QCloudOneSentenceRecognizer
import com.tencent.cloud.qcloudasrsdk.onesentence.QCloudOneSentenceRecognizerListener
import org.json.JSONObject

class VoiceRecognizer(
    private val activity: Activity
) {
    private var recognizer: QCloudOneSentenceRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appId = activity.getString(R.string.tencent_app_id)
    private val secretId = activity.getString(R.string.tencent_secret_id)
    private val secretKey = activity.getString(R.string.tencent_secret_key)
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    fun setCallbacks(onResult: (String) -> Unit, onError: (String) -> Unit) {
        onResultCallback = onResult
        onErrorCallback = onError
    }

    fun startRecording(): Boolean {
        Log.d(TAG, "startRecording: 开始初始化语音识别")
        try {
            Log.d(TAG, "startRecording: 创建 QCloudOneSentenceRecognizer, appId=$appId")
            recognizer = QCloudOneSentenceRecognizer(activity, appId, secretId, secretKey)
            Log.d(TAG, "startRecording: 设置回调")
            recognizer?.setCallback(object : QCloudOneSentenceRecognizerListener {
                override fun didStartRecord() {
                    Log.d(TAG, "didStartRecord: 开始录音")
                }

                override fun didStopRecord() {
                    Log.d(TAG, "didStopRecord: 停止录音")
                }

                override fun recognizeResult(
                    recognizer: QCloudOneSentenceRecognizer?,
                    result: String?,
                    exception: Exception?
                ) {
                    Log.d(TAG, "recognizeResult: result=$result, exception=$exception")
                    if (!result.isNullOrEmpty()) {
                        val text = try {
                            val json = JSONObject(result)
                            json.optJSONObject("Response")?.optString("Result") ?: result
                        } catch (e: Exception) {
                            Log.e(TAG, "recognizeResult: JSON解析失败, error=${e.message}")
                            result
                        }
                        Log.d(TAG, "recognizeResult: 识别成功, text=$text")
                        mainHandler.post { onResultCallback?.invoke(text) }
                    } else {
                        Log.e(TAG, "recognizeResult: 识别失败, exception=${exception?.message}")
                        mainHandler.post { onErrorCallback?.invoke(exception?.message ?: "识别失败") }
                    }
                }
            })
            recognizer?.setDefaultParams(0, 0, 0, 1, null, "16k_zh")
            Log.d(TAG, "startRecording: 调用 recognizeWithRecorder")
            recognizer?.recognizeWithRecorder()
            Log.d(TAG, "startRecording: recognizeWithRecorder 已调用")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "startRecording: 初始化失败, error=${e.message}", e)
            mainHandler.post { onErrorCallback?.invoke("初始化语音识别失败: ${e.message}") }
            return false
        }
    }

    fun stopRecordingAndRecognize() {
        Log.d(TAG, "stopRecordingAndRecognize: 停止录音并识别")
        try {
            recognizer?.stopRecognizeWithRecorder()
            Log.d(TAG, "stopRecordingAndRecognize: stopRecording 已调用")
        } catch (e: Exception) {
            Log.e(TAG, "stopRecordingAndRecognize: 停止失败, error=${e.message}", e)
        }
    }

    fun release() {
        Log.d(TAG, "release: 释放资源")
        try {
            //recognizer?.stopRecording()
        } catch (e: Exception) {
            Log.e(TAG, "release: 停止录音失败, error=${e.message}")
        }
        recognizer = null
    }

    companion object {
        private const val TAG = "VoiceRecognizer"
    }
}
