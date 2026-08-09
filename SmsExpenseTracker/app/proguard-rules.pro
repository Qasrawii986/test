# Keep Room entities' field names for schema stability.
-keepclassmembers class com.smsexpense.tracker.data.local.entity.** { <fields>; }

# OkHttp platform warnings
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
