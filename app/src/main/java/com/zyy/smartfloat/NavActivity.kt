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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.ViewModelProvider
import com.zyy.smartfloat.ui.theme.SmartFloatTheme
import com.zyy.smartfloat.unity.bridge.MyUnityBridge
import com.zyy.smartfloat.unity.flagment.MyUnityFragment
import com.zyy.smartfloat.unity.viewModel.UnityViewModel
import com.zyy.smartfloat.viewmodel.MainViewModel
import com.zyy.smartfloat.viewmodel.SettingsViewModel

/**
 * 底部导航的各个页面。顺序即底栏展示顺序。
 */
enum class NavPage(val label: String, val iconRes: Int) {
    HOME("home", R.drawable.ic_app),
    MODEL("model", R.drawable.ic_model),
    SKILL("skills", R.drawable.ic_complete),
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

        Box{
            Scaffold(
                modifier = Modifier.fillMaxSize().background(color = Color.Transparent),
                bottomBar = {
                    Row{
                        Box(modifier = Modifier.weight(1f)){
                            NavigationBar {
                                pages.forEachIndexed { index, page ->
                                    NavigationBarItem(
                                        selected = selectedIndex == index,
                                        onClick = { unityViewModel.setSelectedIndex(index) },
                                        icon = {
                                            Icon(
                                                painter = painterResource(id = page.iconRes),
                                                contentDescription = page.label
                                            )
                                        },
                                        label = { Text(page.label) }
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.width(80.dp).background(color = Color.Transparent)
                        ){
                            AndroidView(
                                modifier = Modifier
                                    .width(80.dp)
                                    .height(80.dp)
                                    .padding(bottom = 10.dp),
                                factory = { ctx ->
                                    FragmentContainerView(ctx).apply {
                                        // 给容器一个稳定且唯一的 ID，FragmentManager 通过它查找 Fragment
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
                                    // 触摸事件已在 MyUnityFragment 内部直接拦截 UnityPlayer 的 View
                                }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        // 消耗掉已处理的内边距，避免内部页面（MainScreen 自带 Scaffold）重复加系统栏间距
                        .consumeWindowInsets(innerPadding)
                ) {
                    when (pages[selectedIndex]) {
                        NavPage.HOME -> MainScreen(
                            viewModel = mainViewModel,
                            // 首页里的「统计/模型/技能/设置」按钮直接切到对应 tab
                            onNavigate = { unityViewModel.setSelectedIndex(it.ordinal)}
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
}
