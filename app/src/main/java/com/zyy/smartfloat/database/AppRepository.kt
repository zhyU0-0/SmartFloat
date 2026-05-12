package com.zyy.smartfloat.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class AppRepository(private val database: AppDatabase) {

    // ModelConfig
    private val _allModels = MutableStateFlow<List<ModelConfig>>(emptyList())
    val allModels: Flow<List<ModelConfig>> = _allModels.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelConfig?>(null)
    val activeModel: Flow<ModelConfig?> = _activeModel.asStateFlow()

    // Conversation
    private val _allConversations = MutableStateFlow<List<Conversation>>(emptyList())
    val allConversations: Flow<List<Conversation>> = _allConversations.asStateFlow()

    private val _dailyTokenUsage = MutableStateFlow<List<DailyTokenUsage>>(emptyList())
    val dailyTokenUsage: Flow<List<DailyTokenUsage>> = _dailyTokenUsage.asStateFlow()

    private val _tokenUsageByModel = MutableStateFlow<List<ModelTokenUsage>>(emptyList())
    val tokenUsageByModel: Flow<List<ModelTokenUsage>> = _tokenUsageByModel.asStateFlow()

    private val _totalTokenUsed = MutableStateFlow(0)
    val totalTokenUsed: Flow<Int> = _totalTokenUsed.asStateFlow()

    suspend fun refreshData() = withContext(Dispatchers.IO) {
        _allModels.value = database.getAllModels()
        _activeModel.value = database.getActiveModel()
        _allConversations.value = database.getAllConversations()
        _dailyTokenUsage.value = database.getDailyTokenUsage()
        _tokenUsageByModel.value = database.getTokenUsageByModel()
        _totalTokenUsed.value = database.getTotalTokenUsed()
    }

    // ModelConfig 操作
    suspend fun insertModel(model: ModelConfig): Long = withContext(Dispatchers.IO) {
        val id = database.insertModel(model)
        refreshData()
        id
    }

    suspend fun updateModel(model: ModelConfig) = withContext(Dispatchers.IO) {
        database.updateModel(model)
        refreshData()
    }

    suspend fun deleteModel(model: ModelConfig) = withContext(Dispatchers.IO) {
        database.deleteModel(model)
        refreshData()
    }

    suspend fun setActiveModel(modelId: Long) = withContext(Dispatchers.IO) {
        database.setActiveModel(modelId)
        refreshData()
    }

    fun getActiveModelSync(): ModelConfig? {
        return database.getActiveModel()
    }

    // Conversation 操作
    suspend fun insertConversation(
        remark: String,
        inputToken: Int,
        outputToken: Int,
        answerTime: Long,
        modelId: Long,
        modelName: String
    ): Long = withContext(Dispatchers.IO) {
        val conversation = Conversation(
            remark = remark,
            inputToken = inputToken,
            outputToken = outputToken,
            answerTime = answerTime,
            modelId = modelId,
            modelName = modelName
        )
        val id = database.insertConversation(conversation)
        refreshData()
        id
    }

    suspend fun deleteConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        database.deleteConversation(conversation)
        refreshData()
    }

    suspend fun deleteAllConversations() = withContext(Dispatchers.IO) {
        database.deleteAllConversations()
        refreshData()
    }
}
