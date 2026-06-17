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

# 保留 Retrofit 接口
-keep interface com.zyy.smartfloat.api.** { *; }

# ML Kit 规则
-dontwarn com.google.mlkit.**
-keep class com.google.mlkit.** { *; }

# 腾讯云 ASR
-keep class com.tencent.asr.** { *; }