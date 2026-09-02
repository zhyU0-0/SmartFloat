package com.zyy.smartfloat.network

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

interface PackageApi {
    @Streaming
    @GET("version/getPackage")
    suspend fun getPackage(
        @Query("id") id: Long
    ): ResponseBody

    @GET("version/getVersion")
    suspend fun getVersion(): BaseResponse<AppVersion>
}
