package com.zyy.smartfloat.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.database.AddModel
import com.zyy.smartfloat.database.ModelConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ModelManagerViewModel : ViewModel() {
    private val repository = MyApp.repository

    private val _models = MutableStateFlow<List<ModelConfig>>(emptyList())
    val models: StateFlow<List<ModelConfig>> = _models.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allModels.collect {
                _models.value = it
            }
        }
    }

    fun loadModels() {
        viewModelScope.launch {
            repository.refreshData()
        }
    }

    fun addModel(modelName: String, apiKey: String, baseUrl: String) {
        viewModelScope.launch {
            val model = ModelConfig(
                modelName = modelName,
                apiKey = apiKey,
                baseUrl = baseUrl,
                isActive = _models.value.isEmpty(),
                createdAt = System.currentTimeMillis()
            )
            repository.insertModel(model)
        }
    }

    fun setActiveModel(modelId: Long) {
        viewModelScope.launch {
            repository.setActiveModel(modelId)
        }
    }

    fun deleteModel(model: ModelConfig) {
        viewModelScope.launch {
            repository.deleteModel(model)
        }
    }

    fun updateModel(modelId: Long, modelName: String, apiKey: String, baseUrl: String) {
        viewModelScope.launch {
            val model = _models.value.find { it.id == modelId }
            if (model != null) {
                val updatedModel = model.copy(
                    modelName = modelName,
                    apiKey = apiKey,
                    baseUrl = baseUrl
                )
                repository.updateModel(updatedModel)
            }
        }
    }

    fun getClipboardContent(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = clipboard.primaryClip

        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            Log.d("MainActivity getClipboardContent", item.toString())
            return item.text?.toString() ?: ""
        } else {
            Log.d("MainActivity getClipboardContent", "null")
        }
        return ""
    }

    fun copyAddMode(context: Context, addModel: AddModel) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val oldClip = clipboard.primaryClip
        val gson = Gson()
        val addModeText = gson.toJson(addModel)
        val newClip = ClipData.newPlainText("add_model", addModeText)
        if (oldClip != null) {
            for (i in 0 until oldClip.itemCount) {
                newClip.addItem(oldClip.getItemAt(i))
            }
        }
        Toast.makeText(context, "复制成功", Toast.LENGTH_SHORT).show()
        clipboard.setPrimaryClip(newClip)
    }

}
