package com.zyy.smartfloat.database

data class ModelConfig(
    val id: Long = 0,
    val modelName: String,
    val apiKey: String,
    val baseUrl: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class AddModel(
    var modelName: String,
    var apiKey: String,
    var baseUrl: String
)