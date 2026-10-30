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

    private val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

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
        editingIndex = -1
        _selectedIndex.value = -1
    }

    fun addTouchEvent(event: SFTouchEvent){
        _composition.value = _composition.value + event
    }

    private var editingIndex: Int = -1

    fun newTouchEvent(type: Int){
        //底部弹窗创建事件：加入事件列表并设为当前事件，坐标由设计平台覆盖
        val event = getEmptyTouchEvent().copy(type = type)
        _composition.value += event
        editingIndex = _composition.value.size - 1
        touchEvent.value = event
        _selectedIndex.value = editingIndex
    }

    fun selectEvent(index: Int){
        //点击事件列表项：高亮该项，并把该事件作为当前事件供设计平台编辑
        _composition.value.getOrNull(index)?.let {
            editingIndex = index
            _selectedIndex.value = index
            touchEvent.value = it
        }
    }

    fun updateDelay(index: Int, delay: Long){
        //修改延时事件的时长
        val event = _composition.value.getOrNull(index) ?: return
        if (event.type != 0) return
        val updated = event.copy(delay = delay)
        _composition.value = _composition.value.toMutableList().also { it[index] = updated }
        if (editingIndex == index) {
            touchEvent.value = updated
        }
    }

    fun deleteEvent(index: Int){
        val list = _composition.value
        if (index !in list.indices) return
        _composition.value = list.toMutableList().also { it.removeAt(index) }
        if (editingIndex == index) {
            //删除的正是当前编辑的事件，清空编辑状态
            editingIndex = -1
            _selectedIndex.value = -1
            touchEvent.value = getEmptyTouchEvent()
        } else if (editingIndex > index) {
            editingIndex--
        }
        if (_selectedIndex.value > index) {
            _selectedIndex.value--
        }
    }

    fun copyEvent(index: Int){
        //复制该事件并插入到它的下面
        val list = _composition.value
        if (index !in list.indices) return
        val copied = list[index]
        _composition.value = list.toMutableList().also { it.add(index + 1, copied) }
        if (editingIndex > index) editingIndex++
        if (_selectedIndex.value > index) _selectedIndex.value++
    }

    fun moveEvent(from: Int, to: Int){
        //拖动调整事件先后顺序
        val list = _composition.value
        if (from !in list.indices || to !in list.indices || from == to) return
        val mutable = list.toMutableList()
        val event = mutable.removeAt(from)
        mutable.add(to, event)
        _composition.value = mutable
        editingIndex = adjustIndexAfterMove(editingIndex, from, to)
        _selectedIndex.value = adjustIndexAfterMove(_selectedIndex.value, from, to)
    }

    private fun adjustIndexAfterMove(index: Int, from: Int, to: Int): Int = when {
        index < 0 -> index
        index == from -> to
        from < index && to >= index -> index - 1
        from > index && to <= index -> index + 1
        else -> index
    }

    fun recordTouchEvent(event: SFTouchEvent){
        //设计平台的行为只覆盖当前事件（同步更新事件列表中对应项）
        touchEvent.value = event
        if (editingIndex in _composition.value.indices) {
            _composition.value = _composition.value.toMutableList().also { it[editingIndex] = event }
        }
        Log.d("TouchDesign", "记录事件: $event")
    }

    fun selectComposition(index: Int){
        //点击组合列表项，事件列表切换展示该组合
        _touchEventCompositions.value.getOrNull(index)?.let {
            _composition.value = it.composition
            editingIndex = -1
            _selectedIndex.value = -1
        }
    }

    fun saveCompositions(){
        //预留保存接口
    }



}