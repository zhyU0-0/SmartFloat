package com.zyy.smartfloat.viewmodel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.zyy.smartfloat.database.InstructionRecord
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.service.FloatingWindowService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    override fun onCleared() {
        context?.let { ctx ->
            receiver?.let { LocalBroadcastManager.getInstance(ctx).unregisterReceiver(it) }
        }
        super.onCleared()
    }
}
