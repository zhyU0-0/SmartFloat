package com.zyy.smartfloat.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val UPLOAD_BASE_URL = "http://47.112.208.118:9080/"
    private const val LLM_BASE_URL = "https://ark.cn-beijing.volces.com/"

    val uploadApi: UploadApi by lazy { uploadRetrofit.create(UploadApi::class.java) }
    val llmApi: LlmApi by lazy { llmRetrofit.create(LlmApi::class.java) }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    private val uploadRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(UPLOAD_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val llmRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(LLM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
