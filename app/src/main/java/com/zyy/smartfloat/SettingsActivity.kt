package com.zyy.smartfloat

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.zyy.smartfloat.viewmodel.SettingsViewModel
import com.zyy.smartfloat.ui.theme.SmartFloatTheme


class SettingsActivity : ComponentActivity() {
    private lateinit var viewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        viewModel.init(this)

        setContent {
            SmartFloatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    
    var enableImage by remember { mutableStateOf(viewModel.isEnableImage()) }
    var enableEnglish by remember { mutableStateOf(viewModel.isEnableEnglish()) }
    var maxLoops by remember { mutableStateOf(viewModel.getMaxLoops().toString()) }
    var tokenThreshold by remember { mutableStateOf(viewModel.getTokenThreshold().toString()) }
    var showImageWarningDialog by remember { mutableStateOf(false) }

    if (showImageWarningDialog) {
        AlertDialog(
            onDismissRequest = {
                showImageWarningDialog = false
            },
            title = { Text("提示") },
            text = { Text("图片识别功能需要使用多模态大模型，请确认当前配置的模型支持图片识别") },
            confirmButton = {
                Button(
                    onClick = {
                        enableImage = true
                        viewModel.setEnableImage(true)
                        showImageWarningDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showImageWarningDialog = false
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "设置",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "启用图片识别",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "允许应用进行截图并识别图片中的内容",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableImage,
                        onCheckedChange = { checked ->
                            if (checked) {
                                showImageWarningDialog = true
                            } else {
                                enableImage = false
                                viewModel.setEnableImage(false)
                            }
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "启用英文识别",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "允许应用识别英文文本内容",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enableEnglish,
                        onCheckedChange = {
                            enableEnglish = it
                            viewModel.setEnableEnglish(it)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "最大循环次数",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "AI执行任务的最大循环次数，防止无限循环",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = maxLoops,
                        onValueChange = { maxLoops = it },
                        modifier = Modifier.width(80.dp),
                        label = { Text("次数") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    Button(
                        onClick = {
                            val value = maxLoops.toIntOrNull() ?: 100
                            viewModel.setMaxLoops(value)
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Token阈值提醒",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "今日Token消耗超过此值时发送通知，0表示不提醒",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tokenThreshold,
                        onValueChange = { tokenThreshold = it },
                        modifier = Modifier.width(100.dp),
                        label = { Text("Token") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default.copy(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )
                    Button(
                        onClick = {
                            val value = tokenThreshold.toIntOrNull() ?: 100000
                            viewModel.setTokenThreshold(value)
                            Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
