package com.zyy.smartfloat

import android.os.Bundle
import android.util.Log
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
import com.zyy.smartfloat.unity.flagment.MyUnityFragment
import com.zyy.smartfloat.viewmodel.MainViewModel
import com.zyy.smartfloat.viewmodel.SettingsViewModel

/**
 * 底部导航的各个页面。顺序即底栏展示顺序。
 */
enum class NavPage(val label: String, val iconRes: Int) {
    HOME("首页", R.drawable.ic_app),
    MODEL("模型", R.drawable.ic_model),
    SKILL("技能", R.drawable.ic_complete),
    STATISTICS("统计", R.drawable.ic_analytics),
    SETTINGS("设置", R.drawable.ic_setting)
}

/**
 * 应用主容器：底部导航栏 + 在同一个 Activity 内切换 5 个页面。
 * 右侧固定 200dp 显示 Unity Fragment，底部导航栏不延伸到该区域。
 */
class NavActivity : FragmentActivity() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var settingsViewModel: SettingsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        mainViewModel = ViewModelProvider(this)[MainViewModel::class.java]
        mainViewModel.init(applicationContext)

        // SettingsScreen 依赖 SharedPreferences，必须先 init
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        settingsViewModel.init(this)

        setContent {
            SmartFloatTheme {
                NavScreen(
                    mainViewModel = mainViewModel,
                    settingsViewModel = settingsViewModel
                )
            }
        }
    }
}

@Composable
fun NavScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel
) {
    val pages = NavPage.values()
    var selectedIndex by rememberSaveable { mutableStateOf(NavPage.HOME.ordinal) }
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
                                        onClick = { selectedIndex = index },
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
                            onNavigate = { selectedIndex = it.ordinal }
                        )
                        NavPage.MODEL -> ModelManagerScreen()
                        NavPage.SKILL -> SkillsManagerScreen()
                        NavPage.STATISTICS -> StatisticsScreen()
                        NavPage.SETTINGS -> SettingsScreen(viewModel = settingsViewModel)
                    }
                }
            }
        }
        // 右侧：Unity Fragment，固定 200dp 宽，与左侧导航/内容并排，底部导航栏不覆盖此区域

    }
}
