package com.zyy.smartfloat.skills.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.database.AddSkills
import com.zyy.smartfloat.database.SFTouchEvent
import com.zyy.smartfloat.database.SFTouchEventComposition
import com.zyy.smartfloat.database.SFTouchPosition
import com.zyy.smartfloat.database.Skills
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TouchDesignViewModel : ViewModel() {
    private val repository = MyApp.Companion.repository

    private val _touchEventCompositions = MutableStateFlow<List<SFTouchEventComposition>>(emptyList())
    val touchEventCompositions: StateFlow<List<SFTouchEventComposition>> = _touchEventCompositions.asStateFlow()

    private val _composition = MutableStateFlow<List<SFTouchEvent>>(emptyList())
    val composition: StateFlow<List<SFTouchEvent>> = _composition.asStateFlow()

    val touchEvent = MutableStateFlow(getEmptyTouchEvent())

    fun getEmptyTouchEvent(): SFTouchEvent {
        return SFTouchEvent(
            0L,
            1,
            SFTouchPosition(0.0,0.0),
            emptyList(),
            SFTouchPosition(0.0,0.0),
            0L
        )
    }

    init {
        viewModelScope.launch {
            /*repository.allSkills.collect {
                _skills.value = it
            }*/
        }
    }

    fun addComposition(){
        //把当前事件列表存为一个组合，然后清空事件列表开始新组合
        val current = _composition.value
        if (current.isEmpty()) return
        _touchEventCompositions.value = _touchEventCompositions.value + SFTouchEventComposition(current)
        _composition.value = emptyList()
    }

    fun addTouchEvent(event: SFTouchEvent){
        _composition.value = _composition.value + event
    }

    fun selectComposition(index: Int){
        //点击组合列表项，事件列表切换展示该组合
        _touchEventCompositions.value.getOrNull(index)?.let {
            _composition.value = it.composition
        }
    }

    fun saveCompositions(){
        //预留保存接口
    }



}