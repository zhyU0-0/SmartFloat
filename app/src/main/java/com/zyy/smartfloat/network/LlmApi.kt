package com.zyy.smartfloat.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface LlmApi {
    @POST("api/v3/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") auth: String,
        @Body request: LlmRequest
    ): LlmResponse
}

data class LlmRequest(
    val model: String,
    val messages: List<LlmMessage>
)

data class LlmMessage(
    val role: String,
    val content: List<LlmContent>
)

data class LlmResponseMessage(
    val role: String,
    val content: String,
    val reasoning_content: String
)

data class LlmContent(
    val type: String,
    val text: String? = null,
    val image_url: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

data class LlmResponse(
    val id: String,
    @SerializedName("object")
    val objectField: String,
    val created: Long,
    val model: String,
    val choices: List<LlmChoice>,
    val usage: LlmUsage
)

data class LlmChoice(
    val index: Int,
    val message: LlmResponseMessage,
    val finish_reason: String
)

data class LlmUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)


//************************ 提示词 和 返回值 **********************//