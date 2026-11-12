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
    //当前技能关联且与屏幕方向一致的组合列表，供大模型按 index 选择
    val compositions: List<OutputComposition> = emptyList()
)

data class OutputComposition(
    val name: String,
    val description: String
)