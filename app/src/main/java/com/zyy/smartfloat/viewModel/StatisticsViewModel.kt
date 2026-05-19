package com.zyy.smartfloat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zyy.smartfloat.MyApp
import com.zyy.smartfloat.database.Conversation
import com.zyy.smartfloat.database.DailyTokenUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.util.Log

class StatisticsViewModel : ViewModel() {
    private val repository = MyApp.repository

    private val _allConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val allConversations: StateFlow<List<Conversation>> = _allConversations.asStateFlow()

    private val _dailyTokenUsage = MutableStateFlow<List<DailyTokenUsage>>(emptyList())
    val dailyTokenUsage: StateFlow<List<DailyTokenUsage>> = _dailyTokenUsage.asStateFlow()

    private val _totalTokenUsed = MutableStateFlow(0)
    val totalTokenUsed: StateFlow<Int> = _totalTokenUsed.asStateFlow()

    init {
        viewModelScope.launch {
            repository.allConversations.collect { list ->
                _allConversations.value = list
            }
        }
        
        viewModelScope.launch {
            repository.dailyTokenUsage.collect { list ->
                _dailyTokenUsage.value = list
            }
        }
        
        viewModelScope.launch {
            repository.totalTokenUsed.collect { total ->
                Log.d("total1", total.toString())
                _totalTokenUsed.value = total
            }
        }
    }

    fun loadData() {
        viewModelScope.launch {
            repository.refreshData()
        }
    }
}
