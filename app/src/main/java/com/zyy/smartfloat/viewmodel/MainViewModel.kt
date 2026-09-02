package com.zyy.smartfloat.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.zyy.smartfloat.database.InstructionRecord
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.network.AppVersion
import com.zyy.smartfloat.network.RetrofitClient
import com.zyy.smartfloat.service.FloatingWindowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel : ViewModel() {

    private val _llmResult = MutableStateFlow<List<String>>(emptyList())
    val llmResult: StateFlow<List<String>> = _llmResult.asStateFlow()

    private val _currentInstruction = MutableStateFlow("")
    val currentInstruction: StateFlow<String> = _currentInstruction.asStateFlow()

    private val _instructionRecords = MutableStateFlow<List<InstructionRecord>>(emptyList())
    val instructionRecords: StateFlow<List<InstructionRecord>> = _instructionRecords.asStateFlow()

    private val _imageRecognitionResult = MutableStateFlow("")
    val imageRecognitionResult: StateFlow<String> = _imageRecognitionResult.asStateFlow()

    private val _llmQuestion = MutableStateFlow("点击返回按钮")
    val llmQuestion: StateFlow<String> = _llmQuestion.asStateFlow()

    private val _showAddModelReminder = MutableStateFlow(false)
    val showAddModelReminder: StateFlow<Boolean> = _showAddModelReminder.asStateFlow()

    // 版本/下载相关状态
    private val _versionInfo = MutableStateFlow<AppVersion?>(null)
    val versionInfo: StateFlow<AppVersion?> = _versionInfo.asStateFlow()

    // 是否显示「有新版本，是否更新」的提醒弹窗
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    // 是否显示下载进度弹窗（用户点更新后才出现）
    private val _downloadDialogVisible = MutableStateFlow(false)
    val downloadDialogVisible: StateFlow<Boolean> = _downloadDialogVisible.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _downloadedBytes = MutableStateFlow(0L)
    val downloadedBytes: StateFlow<Long> = _downloadedBytes.asStateFlow()

    private val _totalBytes = MutableStateFlow(0L)
    val totalBytes: StateFlow<Long> = _totalBytes.asStateFlow()

    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    // 下载完成后的 APK 路径，供 Activity 触发安装
    private val _pendingApkPath = MutableStateFlow<String?>(null)
    val pendingApkPath: StateFlow<String?> = _pendingApkPath.asStateFlow()

    companion object {
        private const val TAG = "PackageUpdate"
        private const val SP_NAME = "app_version"
        private const val KEY_INSTALLED_VERSION_ID = "installed_version_id"

        fun getInstalledVersionId(context: Context): Long {
            return context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .getLong(KEY_INSTALLED_VERSION_ID, -1L)
        }

        fun saveInstalledVersionId(context: Context, id: Long) {
            context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_INSTALLED_VERSION_ID, id)
                .apply()
        }
    }

    private var receiver: BroadcastReceiver? = null
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context
        registerReceiver()
        checkAddModelReminder()
    }

    private fun registerReceiver() {
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    FloatingWindowService.ACTION_LLM_RESULT -> {
                        val result = intent.getStringExtra(FloatingWindowService.EXTRA_LLM_RESULT) ?: ""
                        if (result.isNotEmpty()) {
                            synchronized(this@MainViewModel) {
                                val currentList = _llmResult.value.toMutableList()
                                currentList.add(result)
                                _llmResult.value = currentList

                                if (_llmResult.value.size == 1 && _currentInstruction.value.isNotEmpty()) {
                                    val records = _instructionRecords.value.toMutableList()
                                    records.add(0, InstructionRecord(_currentInstruction.value, result))
                                    _instructionRecords.value = records
                                } else if (_llmResult.value.size > 1 && _instructionRecords.value.isNotEmpty()) {
                                    val records = _instructionRecords.value.toMutableList()
                                    val updatedRecord = records[0].copy(remark = _llmResult.value.joinToString("\n"))
                                    records[0] = updatedRecord
                                    _instructionRecords.value = records
                                }
                            }
                        }
                    }
                    FloatingWindowService.ACTION_TASK_START -> {
                        _llmResult.value = emptyList()
                        _imageRecognitionResult.value = ""
                    }
                    FloatingWindowService.ACTION_VOICE_RECOGNITION -> {
                        val text = intent.getStringExtra(FloatingWindowService.EXTRA_VOICE_TEXT) ?: ""
                        if (text.isNotEmpty()) {
                            _llmQuestion.value = text
                            _currentInstruction.value = text
                        }
                    }
                    FloatingWindowService.ACTION_IMAGE_RECOGNITION -> {
                        val text = intent.getStringExtra(FloatingWindowService.EXTRA_IMAGE_TEXT) ?: ""
                        if (text.isNotEmpty()) {
                            _imageRecognitionResult.value = text
                        }
                    }
                }
            }
        }

        context?.let { ctx ->
            LocalBroadcastManager.getInstance(ctx).registerReceiver(
                receiver!!,
                IntentFilter().apply {
                    addAction(FloatingWindowService.ACTION_LLM_RESULT)
                    addAction(FloatingWindowService.ACTION_TASK_START)
                    addAction(FloatingWindowService.ACTION_VOICE_RECOGNITION)
                    addAction(FloatingWindowService.ACTION_IMAGE_RECOGNITION)
                }
            )
        }
    }

    private fun checkAddModelReminder() {
        viewModelScope.launch {
            delay(500)
            if (MyApp.needAddModelReminder) {
                _showAddModelReminder.value = true
            }
        }
    }

    fun setCurrentInstruction(instruction: String) {
        _currentInstruction.value = instruction
    }

    fun dismissAddModelReminder() {
        _showAddModelReminder.value = false
    }

    /**
     * 进入 APP 时调用：调 getVersion 获取最新版本，与 SharedPreferences 中存储的已安装版本 id 比对，
     * 若 id 更大则弹出「是否更新」的提醒弹窗。
     */
    fun checkVersion() {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.packageApi.getVersion()
                if (response.code != 1 || response.data == null) {
                    Log.e(TAG, "获取版本失败 code=${response.code} msg=${response.message}")
                    return@launch
                }

                val version = response.data
                _versionInfo.value = version
                // 输出版本信息
                Log.d(TAG, "PackageVersion: id=${version.id}, version=${version.version}, size=${version.size}, fileName=${version.fileName}")

                val latestId = version.id ?: return@launch
                val ctx = context ?: return@launch
                val installedId = getInstalledVersionId(ctx)

                if (latestId > installedId) {
                    _showUpdateDialog.value = true
                    Log.d(TAG, "发现新版本 latest=$latestId installed=$installedId")
                } else {
                    Log.d(TAG, "已是最新版本 id=$latestId")
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkVersion 异常: ${e.message ?: e.javaClass.simpleName}", e)
            }
        }
    }

    /**
     * 用户点击「更新」后调用：下载新版 APK，显示进度条；下载完成后将路径写入 pendingApkPath，
     * 由 Activity 观察到后触发系统安装 Intent。
     */
    fun downloadAndInstallPackage() {
        viewModelScope.launch {
            val version = _versionInfo.value
            if (version == null) {
                _downloadError.value = "版本信息为空"
                return@launch
            }
            val id = version.id ?: run {
                _downloadError.value = "版本信息缺少 id"
                return@launch
            }

            // 用户点「立即更新」即视为已通知，保存新 ID 防止下次再弹窗
            context?.let { saveInstalledVersionId(it, id) }

            _showUpdateDialog.value = false
            _downloadDialogVisible.value = true
            _downloadError.value = null
            _downloadProgress.value = 0
            _downloadedBytes.value = 0L
            _pendingApkPath.value = null

            try {
                val body = RetrofitClient.packageApi.getPackage(id)
                withContext(Dispatchers.IO) {
                    val expectedSize = version.size ?: 0L
                    val actualTotal = if (expectedSize > 0) expectedSize else body.contentLength().coerceAtLeast(0L)
                    if (actualTotal > 0) _totalBytes.value = actualTotal

                    val ctx = context ?: return@withContext
                    val apkDir = ctx.getExternalFilesDir("apk") ?: ctx.cacheDir
                    if (!apkDir.exists()) apkDir.mkdirs()

                    val fileName = version.fileName?.takeIf { it.isNotBlank() }
                        ?: "SmartFloat_${version.version ?: "unknown"}.apk"
                    val outputFile = File(apkDir, fileName)
                    if (outputFile.exists()) outputFile.delete()

                    var lastReportedPercent = -1
                    body.byteStream().use { input ->
                        outputFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read
                                _downloadedBytes.value = downloaded
                                if (actualTotal > 0) {
                                    val percent = ((downloaded * 100) / actualTotal).toInt().coerceIn(0, 100)
                                    if (percent != lastReportedPercent) {
                                        lastReportedPercent = percent
                                        _downloadProgress.value = percent
                                    }
                                }
                            }
                        }
                    }
                    _downloadProgress.value = 100
                    Log.d(TAG, "下载完成，已保存至 ${outputFile.absolutePath}")
                    _pendingApkPath.value = outputFile.absolutePath
                }
            } catch (e: Exception) {
                _downloadError.value = "下载失败: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    /** 用户在更新提醒弹窗点了「取消/稍后再说」。 */
    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    /** 下载完成或失败后关闭下载弹窗（由 Activity 调用）。 */
    fun dismissDownloadDialog() {
        _downloadDialogVisible.value = false
        _pendingApkPath.value = null
    }

    override fun onCleared() {
        context?.let { ctx ->
            receiver?.let { LocalBroadcastManager.getInstance(ctx).unregisterReceiver(it) }
        }
        super.onCleared()
    }
}
