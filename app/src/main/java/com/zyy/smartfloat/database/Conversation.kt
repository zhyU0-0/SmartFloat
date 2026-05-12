package com.zyy.smartfloat.database


data class Conversation(
    val id: Long = 0,
    val remark: String,
    val inputToken: Int,
    val outputToken: Int,
    val answerTime: Long,
    val modelId: Long,
    val modelName: String,
    val createdAt: Long = System.currentTimeMillis()
)
