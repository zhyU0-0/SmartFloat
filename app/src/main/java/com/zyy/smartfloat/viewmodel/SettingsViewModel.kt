package com.zyy.smartfloat.viewModel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel

class SettingsViewModel : ViewModel() {
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "SmartFloatSettings"
        private const val KEY_ENABLE_IMAGE = "enable_image"
        private const val KEY_ENABLE_ENGLISH = "enable_english"
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
}
