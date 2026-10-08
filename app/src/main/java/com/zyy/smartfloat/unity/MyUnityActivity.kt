package com.zyy.smartfloat.unity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentManager
import com.unity3d.player.UnityPlayer
import com.zyy.smartfloat.R
import com.zyy.smartfloat.unity.bridge.MyUnityBridge
import com.zyy.smartfloat.unity.flagment.MyUnityFragment

class MyUnityActivity : AppCompatActivity() {

    private lateinit var text: TextView;
    private lateinit var startButton: Button;
    private lateinit var stopButton: Button;
    private lateinit var unityReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_unity)
        MyUnityBridge.setContext(this)

        receiverView()
        receiverBroadcast()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (savedInstanceState == null) {
            val fragmentManager: FragmentManager = supportFragmentManager
            fragmentManager.beginTransaction()
                .replace(R.id.unity_view, MyUnityFragment())
                .commit()
        }
    }

    override fun onResume() {
        super.onResume()
        // UnityPlayer 在 resume 时会重新隐藏状态栏，post 到其设置完之后再把状态栏显示回来
        window.decorView.post { showSystemStatusBar() }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 获得焦点时 Unity 可能已切到沉浸式全屏，再次纠正
        if (hasFocus) window.decorView.post { showSystemStatusBar() }
    }

    /**
     * 强制显示系统状态栏：清掉全屏 Flag + 通过 WindowInsetsController 显示 statusBars。
     * 状态栏恢复后系统会重新分发带 top 高度的 WindowInsets，根布局的 padding 才会生效。
     */
    private fun showSystemStatusBar() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        WindowInsetsControllerCompat(window, window.decorView)
            .show(WindowInsetsCompat.Type.statusBars())
    }

    fun receiverView(){
        text = findViewById(R.id.unity_text);
        startButton = findViewById(R.id.unity_button1);
        stopButton = findViewById(R.id.unity_button2);

        startButton.setOnClickListener{
            Log.d("Unity1","StartRotate")
            UnityPlayer.UnitySendMessage("Directional Light", "OnStart", "90")
        }
        stopButton.setOnClickListener {
            Log.d("Unity1","StopRotate")
            UnityPlayer.UnitySendMessage("Directional Light", "OnStop", "0")
        }
    }

    fun receiverBroadcast(){
        unityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == "com.zyy.smartfloat.UNITY_EVENT") {
                    val action = intent.getStringExtra("action")
                    val data = intent.getStringExtra("data")
                    // 更新 UI
                    text.setText("${action} : ${data}")
                }
            }
        }
        val filter = IntentFilter("com.zyy.smartfloat.UNITY_EVENT")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+，必须显式指定
            registerReceiver(unityReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MyUnityBridge.onDestroy();
        unregisterReceiver(unityReceiver);
    }
}
