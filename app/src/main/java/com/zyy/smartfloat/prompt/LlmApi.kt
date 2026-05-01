package com.zyy.smartfloat.prompt



data class LLmResponse(
    val tapPoints: List<TapPoints>,
    val command: String?,
    val isEnd: Boolean,
    val remark: String
)
data class TapPoints(
    val tapX: Double,
    val tapY: Double,
    val delay: Int,
)
data class LLmBody(
    val prompt: String,
    val question: String,
    val maxX: Int,
    val maxY: Int
)


//************************ 提示词 和 返回值 **********************//