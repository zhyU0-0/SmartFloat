package com.zyy.smartfloat.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private const val UPLOAD_BASE_URL = "http://47.112.208.118:5080/"

    val uploadApi: UploadApi by lazy { uploadRetrofit.create(UploadApi::class.java) }

    // 根据传入的 baseUrl 获取 LlmApi
    fun getLlmApi(baseUrl: String): LlmApi {
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        return retrofit.create(LlmApi::class.java)
    }

    private const val MAX_LOG_LENGTH = 3000
    private const val MAX_BINARY_DETECTION = 100

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    private val uploadOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(tokenInterceptor)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }

    private val loggingInterceptor = HttpLoggingInterceptor(object : HttpLoggingInterceptor.Logger {
        override fun log(message: String) {
            if (message.isEmpty()) {
                return
            }

            if (isBinaryContent(message)) {
                println("OkHttp: [Binary data skipped]")
                return
            }

            val trimmedMessage = if (message.length > MAX_LOG_LENGTH) {
                message.substring(0, MAX_LOG_LENGTH) + " [TRUNCATED, total: ${message.length}]"
            } else {
                message
            }
            println("OkHttp: $trimmedMessage")
        }
    }).apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val tokenInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val newRequest = originalRequest.newBuilder()
            .header(
                "token",
                com.zyy.smartfloat.MyApp.instance.getString(com.zyy.smartfloat.R.string.image_token)
            )
            .build()
        chain.proceed(newRequest)
    }

    private fun isBinaryContent(message: String): Boolean {
        var nonPrintableCount = 0
        val checkLength = minOf(message.length, MAX_BINARY_DETECTION)

        for (i in 0 until checkLength) {
            val char = message[i]
            if (char.toInt() < 32 && char != '\n' && char != '\r' && char != '\t') {
                nonPrintableCount++
            }
        }

        return nonPrintableCount > checkLength / 3
    }

    private val uploadRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(UPLOAD_BASE_URL)
            .client(uploadOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}