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
import com.zyy.smartfloat.database.AppDatabase
import com.zyy.smartfloat.database.AddSkills
import com.zyy.smartfloat.database.SFTouchEvent
import com.zyy.smartfloat.database.SFTouchEventComposition
import com.zyy.smartfloat.database.SFTouchPosition
import com.zyy.smartfloat.database.Skills
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TouchDesignViewModel : ViewModel() {
    private val repository = MyApp.Companion.repository
    private val database = AppDatabase.getInstance(MyApp.instance)

    private val _touchEventCompositions = MutableStateFlow<List<SFTouchEventComposition>>(emptyList())
    val touchEventCompositions: StateFlow<List<SFTouchEventComposition>> = _touchEventCompositions.asStateFlow()

    private val _composition = MutableStateFlow<List<SFTouchEvent>>(emptyList())
    val composition: StateFlow<List<SFTouchEvent>> = _composition.asStateFlow()

    val touchEvent = MutableStateFlow(getEmptyTouchEvent())

    private val _selectedIndex = MutableStateFlow(-1)
    val selectedIndex: StateFlow<Int> = _selectedIndex.asStateFlow()

    private val _selectedCompositionIndex = MutableStateFlow(-1)
    val selectedCompositionIndex: StateFlow<Int> = _selectedCompositionIndex.asStateFlow()

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
            //进入页面时读取组合表
            val loaded = withContext(Dispatchers.IO) { database.getAllCompositions() }
            if (_touchEventCompositions.value.isEmpty()) {
                _touchEventCompositions.value = loaded
            }
        }
    }

    fun addComposition(name: String){
        //1. 先把当前事件列表保存回当前组合，并更新数据库
        saveCurrentEventsToComposition()
        //2. 创建新组合，写入数据库
        val finalName = name.trim().ifEmpty { "组合 ${_touchEventCompositions.value.size + 1}" }
        val newComposition = SFTouchEventComposition(name = finalName, composition = emptyList())
        val id = database.insertComposition(newComposition)
        //3. 选中新组合，事件列表切换为新组合（空）
        _touchEventCompositions.value = _touchEventCompositions.value + newComposition.copy(id = id)
        _composition.value = emptyList()
        editingIndex = -1
        _selectedIndex.value = -1
        touchEvent.value = getEmptyTouchEvent()
        _selectedCompositionIndex.value = _touchEventCompositions.value.size - 1
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
        val deletingCurrent = editingIndex == index || _selectedIndex.value == index
        _composition.value = list.toMutableList().also { it.removeAt(index) }
        if (deletingCurrent) {
            //删除的是当前选中的事件：自动选中被删位置的下一项，没有则选中最后一项
            val remaining = _composition.value
            val target = when {
                remaining.isEmpty() -> -1
                index <= remaining.lastIndex -> index
                else -> remaining.lastIndex
            }
            editingIndex = -1
            _selectedIndex.value = -1
            touchEvent.value = getEmptyTouchEvent()
            if (target >= 0) selectEvent(target)
        } else {
            if (editingIndex > index) editingIndex--
            if (_selectedIndex.value > index) _selectedIndex.value--
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
        //切换组合前，先把当前事件列表保存回正在编辑的组合
        saveCurrentEventsToComposition()
        //再加载目标组合的事件列表
        _touchEventCompositions.value.getOrNull(index)?.let {
            _composition.value = it.composition
            _selectedCompositionIndex.value = index
            editingIndex = -1
            _selectedIndex.value = -1
            touchEvent.value = getEmptyTouchEvent()
            //默认选中第一个事件；延时事件不做处理
            it.composition.firstOrNull()
                ?.takeIf { event -> event.type != 0 }
                ?.let { selectEvent(0) }
        }
    }

    private fun saveCurrentEventsToComposition(){
        val selected = _selectedCompositionIndex.value
        val current = _composition.value
        val list = _touchEventCompositions.value
        if (selected in list.indices) {
            //保存回当前选中的组合
            if (list[selected].composition != current) {
                val updated = list[selected].copy(composition = current)
                database.updateComposition(updated)
                _touchEventCompositions.value = list.toMutableList().also {
                    it[selected] = updated
                }
            }
        } else if (current.isNotEmpty()) {
            //没有选中组合时，把当前事件存为新组合，避免切换后丢失
            val newComposition = SFTouchEventComposition(
                name = "组合 ${list.size + 1}",
                composition = current
            )
            val id = database.insertComposition(newComposition)
            _touchEventCompositions.value = list + newComposition.copy(id = id)
        }
    }

    fun deleteComposition(index: Int){
        val list = _touchEventCompositions.value
        if (index !in list.indices) return
        database.deleteComposition(list[index])
        val remaining = list.toMutableList().also { it.removeAt(index) }
        _touchEventCompositions.value = remaining
        val selected = _selectedCompositionIndex.value
        when {
            selected == index -> {
                //被删除的是当前组合：清空事件列表，再自动选中相邻组合（同时默认选中事件）
                _selectedCompositionIndex.value = -1
                _composition.value = emptyList()
                editingIndex = -1
                _selectedIndex.value = -1
                touchEvent.value = getEmptyTouchEvent()
                if (remaining.isNotEmpty()) {
                    selectComposition(index.coerceAtMost(remaining.lastIndex))
                }
            }
            selected > index -> _selectedCompositionIndex.value = selected - 1
        }
    }

    fun saveCompositions(){
        //保存按钮：把当前事件列表写回正在编辑的组合（并同步数据库）
        saveCurrentEventsToComposition()
    }



}