package com.zyy.smartfloat.database

data class InstructionRecord(
    val instruction: String,
    val remark: String,
    val timestamp: Long = System.currentTimeMillis()
)