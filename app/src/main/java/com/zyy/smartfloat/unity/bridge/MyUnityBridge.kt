package com.zyy.smartfloat.unity.bridge

import android.content.Context
import android.content.Intent

object MyUnityBridge {
    private var context: Context? = null
    val UnityAction = "Unity"

    fun setContext(context: Context) {
        this.context = context.applicationContext  // 用 ApplicationContext 避免泄漏
    }

    fun onDestroy() {
        this.context = null
    }

    @JvmStatic
    fun sendToNative(data: String) {
        val ctx = context
        if (ctx == null) {
            println("发送 UnityBridge 广播失败: Context is null")
            return
        }
        val intent = Intent("com.zyy.smartfloat.UNITY_EVENT").apply {
            putExtra("action", UnityAction)
            putExtra("data", data)
            setPackage(ctx.packageName)
        }
        ctx.sendBroadcast(intent)
    }

    @JvmStatic
    fun onGameFinished(result: String) {
        println("Unity 通知: $result")
        sendToNative(result)
    }

    @JvmStatic
    fun onToLable(result: String) {
        println("Unity 通知: $result")
        sendToNative(result)
    }
}