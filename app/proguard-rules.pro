# 保留 Compose 相关类
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes InnerClasses
-keep class kotlin.Metadata { *; }

# 保留 ViewModel 和 LiveData
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# 保留数据库相关类
-keepclassmembers class * extends android.database.sqlite.SQLiteOpenHelper {
    public <init>(...);
}

# 保留序列化类（Gson）
-keep class com.zyy.smartfloat.database.** { *; }

# ===== 网络层（Retrofit + OkHttp + Gson）=====
# 保留 Retrofit 接口（注意包名是 network，不是 api）
-keep interface com.zyy.smartfloat.network.** { *; }
# 保留 Gson 反序列化的数据模型类（字段名不能被混淆，否则解析出 LinkedTreeMap 导致 ClassCastException）
-keep class com.zyy.smartfloat.network.** { *; }
# prompt 包下的 LLmResponse/TapPoints/LLmBody/ProcessHistory 同样被 Gson 序列化/反序列化
-keep class com.zyy.smartfloat.prompt.** { *; }

# Retrofit 官方推荐规则
-keepattributes RuntimeVisibleAnnotations
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp 官方推荐规则
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Gson 官方推荐规则
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-dontwarn sun.misc.**
# Gson 用反射调用序列化模型的无参构造和字段
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ML Kit 规则
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }

# 腾讯云 ASR
-keep class com.tencent.asr.** { *; }

# Unity Bridge：Unity 侧通过 AndroidJavaClass 按全限定名字符串反射调用，类名/包名/方法名都不能混淆
-keep class com.zyy.smartfloat.unity.bridge.** { *; }