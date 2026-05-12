package com.zyy.smartfloat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent {
            SmartFloatTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ModelManagerScreen()
                }
            }
        }
    }
}

@Composable
fun ModelManagerScreen(
    viewModel: ModelManagerViewModel = viewModel()
) {
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        viewModel.loadModels()
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var newModelName by remember { mutableStateOf("") }
    var newApiKey by remember { mutableStateOf("") }
    var newBaseUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
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
                        }
                    )
                }
            }
        }

        // 添加模型对话框
        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("添加新模型") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newModelName,
                            onValueChange = { newModelName = it },
                            label = { Text("模型名称") },
                            placeholder = { Text("例如：doubao-seed-2-0-pro-260215") }
                        )
                        OutlinedTextField(
                            value = newApiKey,
                            onValueChange = { newApiKey = it },
                            label = { Text("API Key") },
                            placeholder = { Text("例如：8a096786-d383-4778-968a-d2e0a9f835df") }
                        )
                        OutlinedTextField(
                            value = newBaseUrl,
                            onValueChange = { newBaseUrl = it },
                            label = { Text("Base URL") },
                            placeholder = { Text("例如：https://ark.cn-beijing.volces.com/") }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newModelName.isNotEmpty() && newApiKey.isNotEmpty()) {
                                viewModel.addModel(newModelName, newApiKey, newBaseUrl)
                                newModelName = ""
                                newApiKey = ""
                                newBaseUrl = ""
                                showAddDialog = false
                            }
                        }
                    ) {
                        Text("确认添加")
                    }
                },
                dismissButton = {
                    Button(onClick = { showAddDialog = false }) {
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
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (isActive) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        }
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

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "API Key: ${model.apiKey}",
                fontSize = 14.sp,
                color = Color.Gray
            )

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
}
