package com.zyy.smartfloat

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zyy.smartfloat.database.InstructionRecord
import com.zyy.smartfloat.service.FloatingWindowService
import com.zyy.smartfloat.utils.VoiceRecognizer
import com.zyy.smartfloat.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        viewModel.init(this)

        setContent {
            val showAddModelReminder by viewModel.showAddModelReminder.collectAsStateWithLifecycle()
            val llmResult by viewModel.llmResult.collectAsStateWithLifecycle()
            val instructionRecords by viewModel.instructionRecords.collectAsStateWithLifecycle()
            val imageRecognitionResult by viewModel.imageRecognitionResult.collectAsStateWithLifecycle()
            val llmQuestion by viewModel.llmQuestion.collectAsStateWithLifecycle()

            if (showAddModelReminder) {
                AlertDialog(
                    onDismissRequest = { viewModel.dismissAddModelReminder() },
                    title = { Text("提示") },
                    text = { Text("当前没有配置任何模型，请先添加模型配置") },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.dismissAddModelReminder()
                                val intent = Intent(this@MainActivity, ModelManagerActivity::class.java)
                                startActivity(intent)
                            }
                        ) {
                            Text("去添加")
                        }
                    },
                    dismissButton = {
                        Button(onClick = {
                            viewModel.dismissAddModelReminder()
                        }) {
                            Text("稍后")
                        }
                    }
                )
            }

            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                FloatingButtonScreen(
                    modifier = Modifier.padding(innerPadding),
                    llmResult = llmResult,
                    instructionRecords = instructionRecords,
                    imageRecognitionResult = imageRecognitionResult,
                    llmQuestion = llmQuestion,
                    onStartFloating = { instruction ->
                        viewModel.setCurrentInstruction(instruction)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingButtonScreen(
    modifier: Modifier = Modifier,
    llmResult: List<String> = emptyList(),
    instructionRecords: List<InstructionRecord> = emptyList(),
    imageRecognitionResult: String = "",
    llmQuestion: String = "点击返回按钮",
    onStartFloating: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var isFloatingShowing by remember { mutableStateOf(false) }
    var localQuestion by remember { mutableStateOf(llmQuestion) }
    var isRecording by remember { mutableStateOf(false) }

    DisposableEffect(llmQuestion) {
        localQuestion = llmQuestion
        onDispose { }
    }

    val voiceRecognizer = remember(activity) {
        activity?.let {
            VoiceRecognizer(
                context = it
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceRecognizer?.release()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 麦克风权限已授予
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFloatingService(context, localQuestion)
            isFloatingShowing = true
        }
    }

    val accessibilityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* user may or may not have enabled it */ }

    val tryStartService: (Context, () -> Unit) -> Unit = { ctx, onReady ->
        onStartFloating(localQuestion)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startFloatingService(ctx, localQuestion)
            onReady()
        }
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            tryStartService(context) { isFloatingShowing = true }
        }
    }

    val isAccessibilityEnabled = isAccessibilityServiceEnabled(context)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 20.dp)
    ) {
        item {
            Text(
                text = if (isFloatingShowing) "悬浮按钮已显示" else "点击按钮显示悬浮窗"
            )
        }

        if (!isAccessibilityEnabled) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "⚠ 无障碍服务未开启，模拟点击将无法生效",
                    color = Color(0xFFE65100),
                    fontSize = 13.sp
                )
            }
            item {
                TextButton(onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    accessibilityLauncher.launch(intent)
                }) {
                    Text("前往开启", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFloatingShowing) Color(0xFF888888) else Color(0xFF6200EE)
                ),
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (isFloatingShowing) {
                                stopFloatingService(context)
                                isFloatingShowing = false
                            } else {
                                if (!Settings.canDrawOverlays(context)) {
                                    requestOverlayPermission(overlayPermissionLauncher)
                                } else {
                                    tryStartService(context) { isFloatingShowing = true }
                                }
                            }
                        }
                    )
                }
            ) {
                Text(
                    text = if (isFloatingShowing) "隐藏悬浮按钮" else "显示悬浮按钮",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        if (imageRecognitionResult.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
            item {
                Text(
                    text = "图片文本识别结果:",
                    color = Color(0xFF2196F3),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE3F2FD)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = imageRecognitionResult,
                        color = Color(0xFF1976D2),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        if (llmResult.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
            item {
                Text(
                    text = "LLM回答 (共${llmResult.size}条):",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(llmResult.size) { index ->
                Text(
                    text = "${index + 1}. ${llmResult[index]}",
                    color = Color(0xFF333333),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }

        if (instructionRecords.isNotEmpty()) {
            item {
                Text(
                    text = "历史记录",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }
            items(instructionRecords) { record ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "指令: ${record.instruction}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF333333)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "结果: ${record.remark}",
                            fontSize = 13.sp,
                            color = Color(0xFF666666)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = {
                        val intent = Intent(context, StatisticsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("查看统计")
                }
                Button(
                    onClick = {
                        val intent = Intent(context, ModelManagerActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("模型管理")
                }
                Button(
                    onClick = {
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("设置")
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(
        AccessibilityServiceInfo.FEEDBACK_ALL_MASK
    )
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}

private fun startFloatingService(context: Context, llmQuestion: String = "") {
    val intent = Intent(context, FloatingWindowService::class.java)
    intent.putExtra(FloatingWindowService.EXTRA_LLM_QUESTION, llmQuestion)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun stopFloatingService(context: Context) {
    val intent = Intent(context, FloatingWindowService::class.java).apply {
        action = FloatingWindowService.ACTION_STOP
    }
    context.startService(intent)
}

private fun requestOverlayPermission(
    launcher: androidx.activity.compose.ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>
) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:com.zyy.smartfloat")
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    launcher.launch(intent)
}
