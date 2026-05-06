package com.zyy.smartfloat

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission

class VoiceRecognizer(
    private val context: Context
) {
    private var audioRecorder: AudioRecorder? = null
    private var asrApi: QCloudAsrApi? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val appId = context.getString(R.string.tencent_app_id)
    private val secretId = context.getString(R.string.tencent_secret_id)
    private val secretKey = context.getString(R.string.tencent_secret_key)
    private var onResultCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null
    private var onStartCallback: (() -> Unit)? = null

    fun setCallbacks(onResult: (String) -> Unit, onError: (String) -> Unit, onStart: (() -> Unit)? = null) {
        onResultCallback = onResult
        onErrorCallback = onError
        onStartCallback = onStart
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(): Boolean {
        Log.d(TAG, "startRecording: 开始初始化语音识别")
        
        if (audioRecorder != null) {
            Log.w(TAG, "已有录音器实例，先释放")
            audioRecorder?.release()
        }
        
        audioRecorder = AudioRecorder(context)
        asrApi = QCloudAsrApi(appId, secretId, secretKey)
        
        audioRecorder?.setCallbacks(
            onStart = {
                Log.d(TAG, "didStartRecord: 开始录音")
                mainHandler.post { onStartCallback?.invoke() }
            },
            onStop = { filePath ->
                Log.d(TAG, "录音停止, 文件: $filePath")
                // 调用识别API
                recognizeAudio(filePath)
            },
            onError = { error ->
                Log.e(TAG, "录音错误: $error")
                mainHandler.post { onErrorCallback?.invoke(error) }
            }
        )
        
        return audioRecorder?.startRecording() ?: false
    }

    private fun recognizeAudio(filePath: String) {
        Log.d(TAG, "recognizeAudio: 开始识别, 文件: $filePath")
        
        asrApi?.recognizeAudio(filePath) { result, error ->
            if (result != null) {
                Log.d(TAG, "recognizeAudio: 识别成功, result=$result")
                mainHandler.post { onResultCallback?.invoke(result) }
            } else {
                Log.e(TAG, "recognizeAudio: 识别失败, error=$error")
                mainHandler.post { onErrorCallback?.invoke(error ?: "识别失败") }
            }
        }
    }

    fun stopRecordingAndRecognize() {
        Log.d(TAG, "stopRecordingAndRecognize: 停止录音并识别")
        audioRecorder?.stopRecording()
    }

    fun release() {
        Log.d(TAG, "release: 释放资源")
        audioRecorder?.release()
        audioRecorder = null
        asrApi = null
    }

    companion object {
        private const val TAG = "VoiceRecognizer"
    }
}
