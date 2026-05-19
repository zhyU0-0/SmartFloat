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
data class LLmBody(
    val prompt: String,
    val question: String,
    val maxX: Int,
    val maxY: Int,
    val history: List<ProcessHistory>,
    var content: String?
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