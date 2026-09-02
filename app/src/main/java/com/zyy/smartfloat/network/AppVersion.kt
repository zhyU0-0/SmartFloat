package com.zyy.smartfloat.network

import java.time.LocalDateTime


data class AppVersion(
    val id: Long? = null,

    val size: Long? = null,

    val version: String? = null,

    //APK文件在服务器上的文件名
    val fileName: String? = null,

    val createdTime: String? = null
)

