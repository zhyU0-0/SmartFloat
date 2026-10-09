package com.zyy.smartfloat.unity.viewModel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat.registerReceiver
import androidx.lifecycle.ViewModel
import com.unity3d.player.UnityPlayer
import com.zyy.smartfloat.NavPage
import com.zyy.smartfloat.unity.bridge.MyUnityBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UnityViewModel: ViewModel() {
    val TAB = "UnityViewModel"
    lateinit var unityReceiver: BroadcastReceiver
    private val _selectedIndex = MutableStateFlow(NavPage.HOME.ordinal)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    fun setSelectedIndex(index:Int){
        _selectedIndex.value = index
        if(index == 0)sendToLabel(NavPage.HOME.label)
        if(index == 1)sendToLabel(NavPage.MODEL.label)
        if(index == 2)sendToLabel(NavPage.SKILL.label)
        if(index == 3)sendToLabel(NavPage.STATISTICS.label)
        if(index == 4)sendToLabel(NavPage.SETTINGS.label)
    }

    fun sendToLabel(label: String){
        Log.d(TAB,"sendToLabel ${label}")
        when(label){
            NavPage.MODEL.label-> UnityPlayer.UnitySendMessage("CubeIcon", "GotoModel","")
            NavPage.HOME.label-> UnityPlayer.UnitySendMessage("CubeIcon", "GotoHome","")
            NavPage.SKILL.label-> UnityPlayer.UnitySendMessage("CubeIcon", "GotoSkills","")
            NavPage.STATISTICS.label-> UnityPlayer.UnitySendMessage("CubeIcon", "GotoSummary","")
            NavPage.SETTINGS.label-> UnityPlayer.UnitySendMessage("CubeIcon", "GotoSetting","")
        }
    }

    fun processUnityBroadcast(intent: Intent){
        if (intent.action == "com.zyy.smartfloat.UNITY_EVENT") {
            val action = intent.getStringExtra("action")
            val data = intent.getStringExtra("data")
            // 更新 UI
            if(action != MyUnityBridge.UnityAction) return
            if(data != null ){
                when(data){
                    NavPage.HOME.label-> _selectedIndex.value = 0
                    NavPage.MODEL.label-> _selectedIndex.value = 1
                    NavPage.SKILL.label-> _selectedIndex.value = 2
                    "summer"-> _selectedIndex.value = 3
                    NavPage.SETTINGS.label-> _selectedIndex.value = 4
                }

            }
        }
    }
    fun sendRotate(dx: Float,dy: Float){
        UnityPlayer.UnitySendMessage("CubeIcon", "ApplyRotation","{\"rotate\":[0,${dx.toInt()/-2},${dy.toInt()/-2}]}")
        //UnityPlayer.UnitySendMessage("CubeIcon", "ApplyRotation","{\"rotate\":[0,-90,0]}")
    }
    //吸附到最近面
    fun sendSnap(){
        UnityPlayer.UnitySendMessage("CubeIcon", "SnapToNearest","")
    }
}