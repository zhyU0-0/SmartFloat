package com.zyy.smartfloat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zyy.smartfloat.database.ModelConfig
import com.zyy.smartfloat.ui.theme.SmartFloatTheme
import kotlinx.coroutines.launch


class ModelManagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFloatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ModelManagerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun ModelManagerScreen(
    modifier: Modifier = Modifier,
    viewModel: ModelManagerViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.loadModels()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingModelId by remember { mutableStateOf<Long?>(null) }
    var newModelName by remember { mutableStateOf("") }
    var newApiKey by remember { mutableStateOf("") }
    var newBaseUrl by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "模型管理",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { showAddDialog = true }) {
                Text("添加模型")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.models.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无模型")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.models) { model ->
                    ModelCard(
                            model = model,
                            isActive = model.isActive,
                            onSelect = {
                                viewModel.setActiveModel(model.id)
                            },
                            onDelete = {
                                viewModel.deleteModel(model)
                            },
                            onEdit = {
                                // 点击编辑时填充数据到弹窗
                                editingModelId = model.id
                                newModelName = model.modelName
                                newApiKey = model.apiKey
                                newBaseUrl = model.baseUrl ?: ""
                                showAddDialog = true
                            }
                        )
                }
            }
        }

        // 添加/编辑模型对话框
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showAddDialog = false 
                    editingModelId = null
                },
                title = { Text(if (editingModelId != null) "编辑模型" else "添加新模型") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newModelName,
                            onValueChange = { newModelName = it },
                            label = { Text("模型名称") },
                            placeholder = { Text("例如：doubao-seed-2-0-pro-26cccc") }
                        )
                        OutlinedTextField(
                            value = newApiKey,
                            onValueChange = { newApiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("例如：8a096786-xxxx-4778-968a-ccc") }
                        )
                        OutlinedTextField(
                            value = newBaseUrl,
                            onValueChange = { newBaseUrl = it },
                            label = { Text("Complete URL") },
                            placeholder = { Text("例如：https://ark.cn-beijing.volces.com/") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newModelName.isNotEmpty() && newApiKey.isNotEmpty() && newBaseUrl.isNotEmpty()) {
                                if (editingModelId != null) {
                                    // 编辑模式：更新模型
                                    viewModel.updateModel(editingModelId!!, newModelName, newApiKey, newBaseUrl)
                                } else {
                                    // 添加模式：添加新模型
                                    viewModel.addModel(newModelName, newApiKey, newBaseUrl)
                                }
                                newModelName = ""
                                newApiKey = ""
                                newBaseUrl = ""
                                editingModelId = null
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    Button(onClick = {
                        newModelName = ""
                        newApiKey = ""
                        newBaseUrl = ""
                        showAddDialog = false 
                        editingModelId = null
                    }) {
                        Text("取消")
                    }
                }
            )
        }
    }
}

@Composable
fun ModelCard(
    model: ModelConfig,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
        // 添加点击事件
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.modelName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (isActive) {
                    Text(
                        text = "当前使用",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isActive) {
                    TextButton(onClick = onSelect) {
                        Text("使用")
                    }
                }
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}

class ModelManagerViewModel : ViewModel() {
    private val repository = MyApp.repository

    var models by mutableStateOf<List<ModelConfig>>(emptyList())
        private set

    init {
        viewModelScope.launch {
            repository.allModels.collect {
                models = it
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
                isActive = models.isEmpty(),
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
            val model = models.find { it.id == modelId }
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
}
