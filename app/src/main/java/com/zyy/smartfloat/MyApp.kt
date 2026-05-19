package com.zyy.smartfloat

import android.app.Application
import com.zyy.smartfloat.database.AppDatabase
import com.zyy.smartfloat.database.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApp : Application() {
    companion object {
        lateinit var instance: MyApp
            private set
        lateinit var repository: AppRepository
            private set
        
        // 标志位：是否需要提醒用户添加模型
        var needAddModelReminder = false
            private set
    }
    
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化数据库
        database = AppDatabase.getInstance(this)
        repository = AppRepository(database)
        
        // 检查是否需要提醒用户添加模型
        checkNeedAddModelReminder()
    }
    
    private fun checkNeedAddModelReminder() {
        CoroutineScope(Dispatchers.IO).launch {
            repository.refreshData()
            val models = database.getAllModels()
            
            // 如果数据库为空，设置标志位提醒用户添加
            needAddModelReminder = models.isEmpty()
        }
    }
    
    // 重置提醒标志
    fun resetAddModelReminder() {
        needAddModelReminder = false
    }
}
