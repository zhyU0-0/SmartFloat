package com.zyy.smartfloat

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.localbroadcastmanager.content.LocalBroadcastManager


class MainActivity : ComponentActivity() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == FloatingWindowService.ACTION_LLM_RESULT) {
                val result = intent.getStringExtra(FloatingWindowService.EXTRA_LLM_RESULT) ?: ""
                if (result.isNotEmpty()) {
                    synchronized(this@MainActivity) {
                        llmResult.value = result
                        if (currentInstruction.value.isNotEmpty()) {
                            instructionRecords.add(0, InstructionRecord(currentInstruction.value, result))
                        }
                    }
                }
            }
        }
    }

    private val llmResult = mutableStateOf("")
    private val currentInstruction = mutableStateOf("")
    private val instructionRecords = mutableStateListOf<InstructionRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter(FloatingWindowService.ACTION_LLM_RESULT)
        )
        setContent {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                FloatingButtonScreen(
                    modifier = Modifier.padding(innerPadding),
                    llmResult = llmResult.value,
                    instructionRecords = instructionRecords,
                    onStartFloating = { instruction ->
                        currentInstruction.value = instruction
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        super.onDestroy()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingButtonScreen(
    modifier: Modifier = Modifier,
    llmResult: String = "",
    instructionRecords: List<InstructionRecord> = emptyList(),
    onStartFloating: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var isFloatingShowing by remember { mutableStateOf(false) }
    var llmQuestion by remember { mutableStateOf("点击返回按钮") }
    var isRecording by remember { mutableStateOf(false) }

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
            voiceRecognizer?.startRecording()
            isRecording = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFloatingService(context, llmQuestion)
            isFloatingShowing = true
        }
    }

    val accessibilityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* user may or may not have enabled it */ }

    val tryStartService: (Context, () -> Unit) -> Unit = { ctx, onReady ->
        onStartFloating(llmQuestion)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startFloatingService(ctx, llmQuestion)
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

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (isFloatingShowing) "悬浮按钮已显示" else "点击按钮显示悬浮窗"
        )

        if (!isAccessibilityEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚠ 无障碍服务未开启，模拟点击将无法生效",
                color = Color(0xFFE65100),
                fontSize = 13.sp
            )
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                accessibilityLauncher.launch(intent)
            }) {
                Text("前往开启", color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = llmQuestion,
            onValueChange = { llmQuestion = it },
            label = { Text("LLM指令") },
            placeholder = { Text("例如：点击返回按钮") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            maxLines = 3
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            var longPressTriggered by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                longPressTriggered = true
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    voiceRecognizer?.setCallbacks(
                                        onResult = { text ->
                                            llmQuestion = text
                                            isRecording = false
                                        },
                                        onError = { }
                                    )
                                    voiceRecognizer?.startRecording()
                                    isRecording = true
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            onPress = {
                                val released = tryAwaitRelease()
                                if (released && longPressTriggered && isRecording) {
                                    voiceRecognizer?.stopRecordingAndRecognize()
                                    isRecording = false
                                }
                                longPressTriggered = false
                            }
                        )
                    }
            ) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isRecording) Color(0xFFE53935) else Color(0xFF03A9F4)
                    )
                ) {
                    Text(
                        text = if (isRecording) "录音中..." else "长按录音",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }

            if (isRecording) {
                Spacer(modifier = Modifier.width(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                voiceRecognizer?.setCallbacks(
                                    onResult = { text ->
                                        llmQuestion = text
                                        isRecording = false
                                    },
                                    onError = { }
                                )
                                voiceRecognizer?.stopRecordingAndRecognize()
                                isRecording = false
                            }
                        )
                    }
                ) {
                    Text(
                        text = "停止并识别",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

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

        if (llmResult.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "LLM回答: $llmResult",
                color = Color(0xFF4CAF50),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (instructionRecords.isNotEmpty()) {
            Text(
                text = "历史记录",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(instructionRecords) { record ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF5F5F5)
                        ),
                        modifier = Modifier.fillMaxWidth()
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
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
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