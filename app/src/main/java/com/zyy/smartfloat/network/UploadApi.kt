package com.zyy.smartfloat.network

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadApi {
    @Multipart
    @POST("hardware/common/upload")
    suspend fun uploadFile(
        @Part file: MultipartBody.Part
    ): BaseResponse<String>

    @retrofit2.http.GET("hardware/common/getPath")
    suspend fun getPath(): BaseResponse<String>
}
