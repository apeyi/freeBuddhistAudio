-keepattributes *Annotation*

# Room entities and models
-keep class com.dharmachakra.fba_android.data.local.** { *; }
-keep class com.dharmachakra.fba_android.domain.model.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Jsoup
-dontwarn org.jsoup.**
-keep class org.jsoup.** { *; }

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Track data class for Gson deserialization
-keep class com.dharmachakra.fba_android.domain.model.Track { *; }
