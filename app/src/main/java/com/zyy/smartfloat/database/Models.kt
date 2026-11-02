package com.zyy.smartfloat.database

data class DailyTokenUsage(
    val date: String,
    val totalToken: Int
)

data class ModelTokenUsage(
    val modelName: String,
    val totalToken: Int
)

data class SkillCompositionRelation(
    val id: Long = 0,
    val skillId: Long,
    val compositionId: Long,
    val description: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class SkillCompositionDetail(
    val relationId: Long,
    val compositionId: Long,
    val compositionName: String,
    val eventsCount: Int,
    val description: String?
)

/**
 * 保存技能时提交的组合关联项。
 * relationId 为 null 表示新增关联，否则表示更新已有关系。
 */
data class SkillCompositionInput(
    val relationId: Long?,
    val compositionId: Long,
    val compositionName: String,
    val description: String?
)
