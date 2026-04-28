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
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.zyy.smartfloat.ui.theme.SmartFloatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SmartFloatTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    FloatingButtonScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingButtonScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isFloatingShowing by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startFloatingService(context)
            isFloatingShowing = true
        }
    }

    val accessibilityLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* user may or may not have enabled it */ }

    val tryStartService: (Context, () -> Unit) -> Unit = { ctx, onReady ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startFloatingService(ctx)
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

        Button(
            onClick = {
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
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isFloatingShowing) Color(0xFF888888) else Color(0xFF6200EE)
            )
        ) {
            Text(
                text = if (isFloatingShowing) "隐藏悬浮按钮" else "显示悬浮按钮",
                color = Color.White
            )
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

private fun startFloatingService(context: Context) {
    val intent = Intent(context, FloatingWindowService::class.java)
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
