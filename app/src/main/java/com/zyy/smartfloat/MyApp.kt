package com.zyy.smartfloat

import android.app.Application
import com.zyy.smartfloat.database.AppDatabase
import com.zyy.smartfloat.database.AppRepository
import com.zyy.smartfloat.database.ModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MyApp : Application() {
    companion object {
        lateinit var instance: MyApp
            private set
        lateinit var repository: AppRepository
            private set
    }
    
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // 初始化数据库
        database = AppDatabase.getInstance(this)
        repository = AppRepository(database)
        
        // 检查并初始化默认模型
        initializeDefaultModel()
    }
    
    private fun initializeDefaultModel() {
        CoroutineScope(Dispatchers.IO).launch {
            repository.refreshData()
            val models = database.getAllModels()
            
            if (models.isEmpty()) {
                // 从 key.xml 读取默认配置
                val modelName = getString(R.string.llm_model)
                val apiKey = getString(R.string.llm_api_key)
                
                if (modelName.isNotEmpty() && apiKey.isNotEmpty()) {
                    val defaultModel = ModelConfig(
                        modelName = modelName,
                        apiKey = apiKey,
                        isActive = true,
                        createdAt = System.currentTimeMillis()
                    )
                    repository.insertModel(defaultModel)
                }
            }
        }
    }
}

