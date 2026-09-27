package com.zyy.smartfloat.database

data class Skills (
    val id: Long = 0,
    val skillsName: String,
    val content: String? = null,
    val clickingTips: String? = null,//点击要点
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class AddSkills(
    val skillsName: String,
    val content: String,
    val clickingTips: String,//点击要点
)

data class OutputSkills(
    val skillsName: String,
    val skillsContent: String,
    val skillsClickingTips: String,//点击要点
)