package com.zyy.smartfloat

data class InstructionRecord(
    val instruction: String,
    val remark: String,
    val timestamp: Long = System.currentTimeMillis()
)