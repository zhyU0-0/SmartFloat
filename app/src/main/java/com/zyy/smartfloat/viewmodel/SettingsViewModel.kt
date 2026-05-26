package com.zyy.smartfloat.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "SmartFloatSettings"
        private const val KEY_ENABLE_IMAGE = "enable_image"
        private const val KEY_ENABLE_ENGLISH = "enable_english"
        private const val KEY_MAX_LOOPS = "max_loops"
        private const val KEY_TOKEN_THRESHOLD = "token_threshold"
        private const val DEFAULT_MAX_LOOPS = 100
        private const val DEFAULT_TOKEN_THRESHOLD = 100000
    }

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isEnableImage(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENABLE_IMAGE, false)
    }

    fun setEnableImage(enable: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ENABLE_IMAGE, enable).apply()
    }

    fun isEnableEnglish(): Boolean {
        return sharedPreferences.getBoolean(KEY_ENABLE_ENGLISH, false)
    }

    fun setEnableEnglish(enable: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ENABLE_ENGLISH, enable).apply()
    }

    fun getMaxLoops(): Int {
        return sharedPreferences.getInt(KEY_MAX_LOOPS, DEFAULT_MAX_LOOPS)
    }

    fun setMaxLoops(maxLoops: Int) {
        sharedPreferences.edit().putInt(KEY_MAX_LOOPS, maxLoops).apply()
    }

    fun getTokenThreshold(): Int {
        return sharedPreferences.getInt(KEY_TOKEN_THRESHOLD, DEFAULT_TOKEN_THRESHOLD)
    }

    fun setTokenThreshold(threshold: Int) {
        sharedPreferences.edit().putInt(KEY_TOKEN_THRESHOLD, threshold).apply()
    }
}
