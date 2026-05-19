package com.zyy.smartfloat.utils

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AudioRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var outputFile: File? = null
    private var onStartCallback: (() -> Unit)? = null
    private var onStopCallback: ((String) -> Unit)? = null
    private var onErrorCallback: ((String) -> Unit)? = null

    companion object {
        private const val TAG = "AudioRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    fun setCallbacks(onStart: () -> Unit, onStop: (String) -> Unit, onError: (String) -> Unit) {
        onStartCallback = onStart
        onStopCallback = onStop
        onErrorCallback = onError
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(): Boolean {
        if (isRecording) {
            Log.w(TAG, "已经在录音中")
            return false
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            if (bufferSize <= 0) {
                Log.e(TAG, "无法获取音频缓冲区大小")
                onErrorCallback?.invoke("无法获取音频缓冲区大小")
                return false
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord 初始化失败")
                onErrorCallback?.invoke("音频录制器初始化失败")
                return false
            }

            // 创建输出文件
            val audioDir = File(context.getExternalFilesDir(null), "audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            outputFile = File(audioDir, "record_${System.currentTimeMillis()}.pcm")

            isRecording = true
            onStartCallback?.invoke()
            Log.d(TAG, "开始录音, 文件: ${outputFile?.absolutePath}")

            // 在后台线程录制
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    audioRecord?.startRecording()

                    val buffer = ByteArray(bufferSize)
                    FileOutputStream(outputFile).use { fos ->
                        while (isRecording) {
                            val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                            if (bytesRead > 0) {
                                fos.write(buffer, 0, bytesRead)
                            }
                        }
                    }

                    audioRecord?.stop()
                    Log.d(TAG, "录音停止, 文件大小: ${outputFile?.length()} bytes")

                    if (outputFile?.exists() == true && outputFile?.length() ?: 0 > 0) {
                        onStopCallback?.invoke(outputFile!!.absolutePath)
                    } else {
                        onErrorCallback?.invoke("录音文件为空")
                    }

                } catch (e: IOException) {
                    Log.e(TAG, "录音失败: ${e.message}", e)
                    onErrorCallback?.invoke("录音失败: ${e.message}")
                } finally {
                    release()
                }
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "启动录音失败: ${e.message}", e)
            onErrorCallback?.invoke("启动录音失败: ${e.message}")
            return false
        }
    }

    fun stopRecording() {
        Log.d(TAG, "停止录音")
        isRecording = false
    }

    fun release() {
        try {
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "释放资源失败: ${e.message}")
        }
    }
}