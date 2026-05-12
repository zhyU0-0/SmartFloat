package com.zyy.smartfloat.database

data class DailyTokenUsage(
    val date: String,
    val totalToken: Int
)

data class ModelTokenUsage(
    val modelName: String,
    val totalToken: Int
)
