# DroidUtools R8 & ProGuard Configuration

# Preserve line number information for debug/stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 1. JNI & Native Method Keep Rules
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.example.bridge.NativeIosBridge { *; }

# 2. Chaquopy (Embedded Python runtime)
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# 3. libsu (Root shell & IPC)
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# 4. Moshi & JSON Model Reflection
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }
-keep class com.example.data.entities.** { *; }
-keep class com.example.updater.** { *; }
-keep class com.example.legal.** { *; }

# 5. Retrofit & OkHttp
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }

# 6. Room Database & SQLite
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# 7. Kotlin Coroutines & Serialization
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
