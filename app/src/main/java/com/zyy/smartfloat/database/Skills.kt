package com.zyy.smartfloat.database

data class Skills (
    val id: Long = 0,
    val skillsName: String,
    val content: String? = null,
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

data class AddSkills(
    val skillsName: String,
    val content: String,
)

data class OutputSkills(
    val skillsName: String,
    val skillsContent: String,
)