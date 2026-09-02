package com.zyy.smartfloat
// 添加 CircleShape 导入
import androidx.compose.foundation.shape.CircleShape
import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
        viewModel.init(applicationContext)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF0052D4),
                    secondary = Color(0xFF4361EE),
                    tertiary = Color(0xFF7209B7),
                    surface = Color(0xFFF9F8FD),
                    background = Color(0xFFF9F8FD),
                    onPrimary = Color.White,
                    onSurface = Color(0xFF1A1A2E),
                )
            ) {
                val showAddModelReminder by viewModel.showAddModelReminder.collectAsStateWithLifecycle()
                val llmResult by viewModel.llmResult.collectAsStateWithLifecycle()
                val instructionRecords by viewModel.instructionRecords.collectAsStateWithLifecycle()
                val imageRecognitionResult by viewModel.imageRecognitionResult.collectAsStateWithLifecycle()
                val llmQuestion by viewModel.llmQuestion.collectAsStateWithLifecycle()

                // 版本更新相关状态
                val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
                val downloadDialogVisible by viewModel.downloadDialogVisible.collectAsStateWithLifecycle()
                val versionInfo by viewModel.versionInfo.collectAsStateWithLifecycle()
                val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
                val downloadedBytes by viewModel.downloadedBytes.collectAsStateWithLifecycle()
                val totalBytes by viewModel.totalBytes.collectAsStateWithLifecycle()
                val downloadError by viewModel.downloadError.collectAsStateWithLifecycle()
                val pendingApkPath by viewModel.pendingApkPath.collectAsStateWithLifecycle()

                // 进入 APP 时检测是否有新版本
                LaunchedEffect(Unit) {
                    viewModel.checkVersion()
                }

                // 下载完成后触发安装
                LaunchedEffect(pendingApkPath) {
                    val path = pendingApkPath
                    if (!path.isNullOrBlank()) {
                        val canInstall = installApk(path)
                        // 若 installApk 返回 false，说明权限不足跳转设置了，
                        // 此时不 dismiss pendingApkPath，等待用户从设置回来后自动重试
                        if (canInstall) {
                            viewModel.dismissDownloadDialog()
                        }
                    }
                }

                // 监听 ON_RESUME：用户从未知来源设置页回来后，重试安装
                val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, pendingApkPath) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME && !pendingApkPath.isNullOrBlank()) {
                            val canInstall = installApk(pendingApkPath!!)
                            if (canInstall) {
                                viewModel.dismissDownloadDialog()
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (showAddModelReminder) {
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissAddModelReminder() },
                        title = {
                            Text(
                                "提示",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0052D4)
                            )
                        },
                        text = {
                            Text(
                                "当前没有配置任何模型，请先添加模型配置",
                                fontSize = 14.sp,
                                color = Color(0xFF555555)
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    viewModel.dismissAddModelReminder()
                                    val intent = Intent(this@MainActivity, ModelManagerActivity::class.java)
                                    startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0052D4)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("去添加", color = Color.White, fontWeight = FontWeight.Medium)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    viewModel.dismissAddModelReminder()
                                }
                            ) {
                                Text("稍后", color = Color(0xFF999999))
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = Color.White
                    )
                }

                // 更新提醒弹窗
                if (showUpdateDialog) {
                    versionInfo?.let { v ->
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissUpdateDialog() },
                            title = {
                                Text(
                                    "发现新版本",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0052D4)
                                )
                            },
                            text = {
                                Column {
                                    Text(
                                        "有新版本可用，建议立即更新。",
                                        fontSize = 14.sp,
                                        color = Color(0xFF333333)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "版本：${v.version ?: "未知"}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF666666)
                                    )
                                    Text(
                                        "大小：${formatBytes(v.size ?: 0L)}",
                                        fontSize = 13.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { viewModel.downloadAndInstallPackage() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0052D4)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("立即更新", color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = { viewModel.dismissUpdateDialog() }
                                ) {
                                    Text("稍后再说", color = Color(0xFF999999))
                                }
                            },
                            shape = RoundedCornerShape(24.dp),
                            containerColor = Color.White
                        )
                    }
                }

                // 下载进度弹窗（用户点更新后才出现）
                if (downloadDialogVisible) {
                    val hasError = downloadError != null
                    AlertDialog(
                        onDismissRequest = {
                            if (hasError) viewModel.dismissDownloadDialog()
                        },
                        title = {
                            Text(
                                if (hasError) "更新失败" else "正在下载更新",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasError) Color(0xFFE53935) else Color(0xFF0052D4)
                            )
                        },
                        text = {
                            Column {
                                versionInfo?.let { v ->
                                    Text(
                                        "版本：${v.version ?: "未知"}",
                                        fontSize = 14.sp,
                                        color = Color(0xFF333333)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                                if (hasError) {
                                    Text(
                                        downloadError ?: "",
                                        fontSize = 13.sp,
                                        color = Color(0xFFE53935)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF0052D4),
                                        trackColor = Color(0xFFE0E0E0)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}  ($downloadProgress%)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF888888)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            if (hasError) {
                                Button(
                                    onClick = { viewModel.dismissDownloadDialog() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0052D4)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("关闭", color = Color.White)
                                }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = Color.White
                    )
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFFF9F8FD)
                ) { innerPadding ->
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
    val activity = context as? Activity
    var isFloatingShowing by remember { mutableStateOf(false) }
    var localQuestion by remember { mutableStateOf(llmQuestion) }

    DisposableEffect(llmQuestion) {
        localQuestion = llmQuestion
        onDispose { }
    }

    val voiceRecognizer = remember(activity) {
        activity?.let {
            VoiceRecognizer(context = it)
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

    var isAccessibilityEnabled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
        onDispose { }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF9F8FD)),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(vertical = 24.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // 渐变图标背景
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF0052D4), Color(0xFF4361EE))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app),//Visibility
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),  // 着色
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Smart Float",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0052D4),
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "智能悬浮助手",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Accessibility Warning Card
        if (!isAccessibilityEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF3E0)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_refresh),//Refresh
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(Color(0xFFFF9800)),  // 着色
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "无障碍服务未开启",
                                    color = Color(0xFFE65100),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "模拟点击功能将无法使用",
                                    color = Color(0xFFBF360C),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    accessibilityLauncher.launch(intent)
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF0052D4)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("开启", fontSize = 13.sp)
                            }
                            IconButton(
                                onClick = {
                                    isAccessibilityEnabled = isAccessibilityServiceEnabled(context)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_refresh),//Refresh
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(Color(0xFF999999)),  // 着色
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Main Floating Button Card
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isFloatingShowing)
                        Color(0xFFE8E8E8)
                    else
                        Color(0xFF0052D4)
                ),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = if (isFloatingShowing) R.drawable.ic_stop else R.drawable.ic_play),//PlayArrow
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(if (isFloatingShowing) Color(0xFF666666) else Color.White),  // 着色
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isFloatingShowing) "隐藏悬浮按钮" else "显示悬浮按钮",
                        color = if (isFloatingShowing) Color(0xFF333333) else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
// Action Buttons Section - 修复按钮大小问题
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 统计按钮
                Button(
                    onClick = {
                        val intent = Intent(context, StatisticsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0052D4)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)  // 移除默认内边距
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_analytics),//Analytics
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),  // 着色
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "统计",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 模型按钮
                Button(
                    onClick = {
                        val intent = Intent(context, ModelManagerActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f).height(48.dp).padding(0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4361EE)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)  // 移除默认内边距
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.padding(0.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_model),//ModelTraining
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),  // 着色
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "模型",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // 设置按钮
                Button(
                    onClick = {
                        val intent = Intent(context, SettingsActivity::class.java)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7209B7)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)  // 移除默认内边距
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_setting),//Settings
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(Color.White),  // 着色
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "设置",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        // Image Recognition Result Section
        if (imageRecognitionResult.isNotEmpty()) {
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(),
                    exit = fadeOut() + slideOutVertically()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFE3F2FD)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_check_circle),//CheckCircle
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(Color(0xFF0052D4)),  // 着色
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "图片识别结果",
                                    color = Color(0xFF0052D4),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = imageRecognitionResult,
                                color = Color(0xFF333333),
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        // LLM Results Section
        if (llmResult.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_done),//Done
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(Color(0xFF4CAF50)),  // 着色
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "LLM 回答记录",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "${llmResult.size} 条",
                                color = Color(0xFF888888),
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            llmResult.takeLast(5).forEach { result ->
                                Surface(
                                    color = Color(0xFFF5F5F5),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = result,
                                        color = Color(0xFF333333),
                                        fontSize = 13.sp,
                                        lineHeight = 20.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            if (llmResult.size > 5) {
                                Text(
                                    text = "还有 ${llmResult.size - 5} 条记录...",
                                    color = Color(0xFF888888),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Empty State for Results
        if (llmResult.isEmpty() && imageRecognitionResult.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFF5F5F5)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "暂无记录",
                            color = Color(0xFF999999),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "使用悬浮窗操作后，结果将显示在这里",
                            color = Color(0xFFBBBBBB),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: List<Color>
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(gradient),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
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
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:com.zyy.smartfloat")
    )
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    launcher.launch(intent)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return if (unitIndex == 0) "$bytes B" else String.format("%.2f %s", value, units[unitIndex])
}

/**
 * 触发系统安装 APK。
 * Android 7.0+ 使用 FileProvider，Android 8.0+ 需引导用户授权「允许安装未知来源应用」。
 * @return true 表示已触发安装 Intent；false 表示权限不足，已跳转设置页等待用户授权。
 */
private fun Activity.installApk(apkPath: String): Boolean {
    val apkFile = java.io.File(apkPath)
    if (!apkFile.exists()) {
        Log.e("PackageUpdate", "installApk: 文件不存在 $apkPath")
        return false
    }

    // Android 8.0+ 需检查是否允许安装未知来源应用
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        if (!packageManager.canRequestPackageInstalls()) {
            Log.d("PackageUpdate", "installApk: 无未知来源安装权限，跳转设置")
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName")
            )
            startActivity(settingsIntent)
            return false
        }
    }

    val apkUri = FileProvider.getUriForFile(
        this,
        "${packageName}.fileprovider",
        apkFile
    )

    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apkUri, "application/vnd.android.package-archive")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
    startActivity(installIntent)
    return true
}