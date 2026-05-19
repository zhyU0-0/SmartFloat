package com.zyy.smartfloat.prompt

import android.content.Context
import com.zyy.smartfloat.R



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
//a_prompt前面的这写字母是为了让Gson在转为Json字符串时，将不变的元素放在前面，匹配更多前缀，多命中缓存
data class LLmBody(
    val a_prompt: String,
    val b_question: String,
    val c_maxX: Int,
    val c_maxY: Int,
    val d_history: List<ProcessHistory>,
    var z_content: String?
)

data class ProcessHistory(
    val process: String,
    val TapPoints: List<TapPoints>
)


fun buildLlmPrompt(context: Context, tapPointsExample: String): String {
    val rawResId = R.raw.llm_prompt
    val template = context.resources.openRawResource(rawResId)
        .bufferedReader()
        .use { it.readText() }
    return template.replace("{TAP_POINTS_EXAMPLE}", tapPointsExample)
}

fun buildLlmPrompt_image(context: Context, tapPointsExample: String): String {
    val rawResId = R.raw.llm_prompt
    val template = context.resources.openRawResource(rawResId)
        .bufferedReader()
        .use { it.readText() }
    return template.replace("{TAP_POINTS_EXAMPLE}", tapPointsExample)
}