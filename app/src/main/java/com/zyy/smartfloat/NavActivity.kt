package com.zyy.smartfloat

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import com.zyy.smartfloat.home.MainScreen
import com.zyy.smartfloat.model.ModelManagerScreen
import com.zyy.smartfloat.setting.SettingsScreen
import com.zyy.smartfloat.ui.theme.SmartFloatTheme
import com.zyy.smartfloat.unity.bridge.MyUnityBridge
import com.zyy.smartfloat.unity.flagment.MyUnityFragment
import com.zyy.smartfloat.unity.viewModel.UnityViewModel
import com.zyy.smartfloat.home.MainViewModel
import com.zyy.smartfloat.setting.SettingsViewModel
import com.zyy.smartfloat.skills.SkillsManagerScreen
import com.zyy.smartfloat.statistics.StatisticsScreen

/**
 * 底部导航的各个页面。顺序即底栏展示顺序。
 */
enum class NavPage(val label: String, val iconRes: Int) {
    HOME("home", R.drawable.ic_app),
    MODEL("model", R.drawable.ic_model),
    SKILL("skills", R.drawable.ic_skills),
    STATISTICS("statistics", R.drawable.ic_analytics),
    SETTINGS("setting", R.drawable.ic_setting)
}

/**
 * 应用主容器：底部导航栏 + 在同一个 Activity 内切换 5 个页面。
 * 右侧固定 200dp 显示 Unity Fragment，底部导航栏不延伸到该区域。
 */
class NavActivity : FragmentActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var unityViewModel: UnityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mainViewModel.init(applicationContext)

        //注册广播接收Unity消息
        unityViewModel = ViewModelProvider(this)[UnityViewModel::class.java]
        MyUnityBridge.setContext(applicationContext)
        receiverBroadcast()

        // SettingsScreen 依赖 SharedPreferences，必须先 init
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        settingsViewModel.init(this)

        setContent {
            SmartFloatTheme {
                NavScreen(
                    mainViewModel = mainViewModel,
                    settingsViewModel = settingsViewModel,
                    unityViewModel = unityViewModel
                )
            }
        }
    }

    fun receiverBroadcast(){
        unityViewModel.unityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                unityViewModel.processUnityBroadcast(intent)
            }
        }
        val filter = IntentFilter("com.zyy.smartfloat.UNITY_EVENT")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+，必须显式指定
            registerReceiver(unityViewModel.unityReceiver, filter, RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MyUnityBridge.onDestroy();
        unregisterReceiver(unityViewModel.unityReceiver);
    }
}

@Composable
fun NavScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    unityViewModel: UnityViewModel
) {
    val pages = NavPage.values()
    val selectedIndex by unityViewModel.selectedIndex.collectAsState()
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                // 自定义悬浮底栏
                Row{
                    NavBottomBar(
                        pages = pages,
                        selectedIndex = selectedIndex,
                        onSelect = { unityViewModel.setSelectedIndex(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),   // 避开系统导航栏
                    )
                    UnitySlot(context = context)
                }

            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
            ) {
                when (pages[selectedIndex]) {
                    NavPage.HOME -> MainScreen(
                        viewModel = mainViewModel,
                        onNavigate = { unityViewModel.setSelectedIndex(it.ordinal) }
                    )
                    NavPage.MODEL -> ModelManagerScreen()
                    NavPage.SKILL -> SkillsManagerScreen()
                    NavPage.STATISTICS -> StatisticsScreen()
                    NavPage.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }
}

@Composable
private fun NavBottomBar(
    pages: Array<NavPage>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧 5 个 tab
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, page ->
                    NavTabItem(
                        page = page,
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavTabItem(
    page: NavPage,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val iconTint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val iconSize = if (selected) 24.dp else 20.dp
    val bgColor = if (selected) accent.copy(alpha = 0.12f) else Color.Transparent

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = page.iconRes),
            contentDescription = page.label,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.height(2.dp))
        /*Text(
            text = page.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )*/
    }
}

@Composable
private fun UnitySlot(context: Context) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                FragmentContainerView(ctx).apply {
                    id = View.generateViewId()
                }
            },
            update = { view ->
                val activity = context as? FragmentActivity
                activity?.supportFragmentManager?.let { fm ->
                    if (fm.findFragmentById(view.id) == null) {
                        fm.beginTransaction()
                            .replace(view.id, MyUnityFragment())
                            .commit()
                    }
                }
            }
        )
    }
}
