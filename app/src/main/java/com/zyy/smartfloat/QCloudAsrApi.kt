package com.zyy.smartfloat

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class QCloudAsrApi(
    private val appId: String,
    private val secretId: String,
    private val secretKey: String
) {
    
    private val okHttpClient = OkHttpClient()
    private val endpoint = "asr.tencentcloudapi.com"
    private val version = "2019-06-14"
    private val region = "ap-beijing"
    private val service = "asr"
    
    companion object {
        private const val TAG = "QCloudAsrApi"
    }
    
    fun recognizeAudio(audioFilePath: String, callback: (String?, String?) -> Unit) {
        Thread {
            try {
                val audioData = File(audioFilePath).readBytes()
                val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
                
                // 构建请求体（业务参数）
                val requestBody = JSONObject().apply {
                    put("ProjectId", 0)
                    put("SubServiceType", 2)
                    put("EngSerViceType", "16k_zh")
                    put("SourceType", 1)
                    put("VoiceFormat", "pcm")
                    put("Data", base64Audio)
                    put("DataLen", audioData.size)
                }.toString()
                
                Log.d(TAG, "请求体长度: ${requestBody.length}")
                
                // 获取时间戳（秒级）
                val timestamp = System.currentTimeMillis() / 1000
                Log.d(TAG, "时间戳: $timestamp")
                
                // 获取日期（UTC时区）
                val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                calendar.timeInMillis = timestamp * 1000
                val dateFormat = SimpleDateFormat("yyyy-MM-dd")
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                val dateStr = dateFormat.format(calendar.time)
                Log.d(TAG, "日期: $dateStr")
                
                // 1. 计算请求体哈希
                val payloadHash = sha256Hex(requestBody)
                Log.d(TAG, "请求体哈希: $payloadHash")
                
                // 2. 构建规范请求串
                // 格式: HTTPMethod\nURI\nQueryString\nHeaders\nSignedHeaders\nPayloadHash
                val canonicalHeaders = """content-type:application/json; charset=utf-8
host:$endpoint
x-tc-action:sentencerecognition"""
                
                val signedHeaders = "content-type;host;x-tc-action"
                
                val canonicalRequest = """POST
/

$canonicalHeaders

$signedHeaders
$payloadHash"""
                
                Log.d(TAG, "规范请求串:\n$canonicalRequest")
                
                // 3. 构建签名串
                val credentialScope = "$dateStr/$service/tc3_request"
                val canonicalRequestHash = sha256Hex(canonicalRequest)
                val stringToSign = """TC3-HMAC-SHA256
$timestamp
$credentialScope
$canonicalRequestHash"""
                
                Log.d(TAG, "签名串:\n$stringToSign")
                
                // 4. 计算签名
                val secretDate = hmacSHA256Bytes(dateStr, "TC3$secretKey")
                val secretService = hmacSHA256Bytes(service, secretDate)
                val secretSigning = hmacSHA256Bytes("tc3_request", secretService)
                val signatureBytes = hmacSHA256Bytes(stringToSign, secretSigning)
                val signature = bytesToHex(signatureBytes)
                
                Log.d(TAG, "secretKey前10字符: ${secretKey.take(10)}...")
                Log.d(TAG, "secretDate (hex): ${bytesToHex(secretDate)}")
                Log.d(TAG, "secretService (hex): ${bytesToHex(secretService)}")
                Log.d(TAG, "secretSigning (hex): ${bytesToHex(secretSigning)}")
                Log.d(TAG, "签名: $signature")
                Log.d(TAG, "SecretId: $secretId")
                Log.d(TAG, "CredentialScope: $credentialScope")
                
                // 5. 构建Authorization头部
                val authorization = "TC3-HMAC-SHA256 Credential=$secretId/$credentialScope, SignedHeaders=$signedHeaders, Signature=$signature"
                
                // 6. 构建请求
                val request = Request.Builder()
                    .url("https://$endpoint/")
                    .header("Host", endpoint)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("X-TC-Action", "SentenceRecognition")
                    .header("X-TC-Version", version)
                    .header("X-TC-Region", region)
                    .header("X-TC-Timestamp", timestamp.toString())
                    .header("Authorization", authorization)
                    .post(requestBody.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()))
                    .build()
                
                Log.d(TAG, "Authorization: $authorization")
                
                // 7. 发送请求
                val response = okHttpClient.newCall(request).execute()
                val responseBody = response.body?.string()
                
                Log.d(TAG, "响应码: ${response.code}, 响应结果: $responseBody")
                
                if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                    try {
                        val json = JSONObject(responseBody)
                        val result = json.optJSONObject("Response")?.optString("Result")
                        if (!result.isNullOrEmpty()) {
                            callback(result, null)
                        } else {
                            val error = json.optJSONObject("Response")?.optJSONObject("Error")
                            val errorMessage = error?.optString("Message") ?: "识别结果为空"
                            val errorCode = error?.optString("Code") ?: ""
                            Log.e(TAG, "API错误: $errorCode - $errorMessage")
                            callback(null, "$errorCode: $errorMessage")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应失败: ${e.message}")
                        callback(null, "解析响应失败: ${e.message}")
                    }
                } else {
                    callback(null, "请求失败: ${response.code}, 响应: $responseBody")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "识别失败: ${e.message}", e)
                callback(null, "识别失败: ${e.message}")
            }
        }.start()
    }
    
    private fun sha256Hex(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return bytesToHex(hash)
    }
    
    private fun hmacSHA256Bytes(data: String, key: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
    
    private fun hmacSHA256Bytes(data: String, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(key, "HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = "0123456789abcdef"[v ushr 4]
            hexChars[i * 2 + 1] = "0123456789abcdef"[v and 0x0F]
        }
        return String(hexChars)
    }
}
