package com.zyy.smartfloat.database

data class ModelConfig(

    val id: Long = 0,
    val modelName: String,
    val apiKey: String,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
