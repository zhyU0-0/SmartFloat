package com.zyy.smartfloat.network

data class BaseResponse<T>(
    val code: Int = 0,
    val message: String = "",
    val data: T? = null
)
