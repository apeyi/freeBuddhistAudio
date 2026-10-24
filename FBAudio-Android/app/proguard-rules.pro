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

# ContentCache round-trips these private nested wrappers through Gson by reflection.
# They live in data.repository (not covered by the model keeps above), so without
# this R8 full-mode strips their generic field signatures and Gson deserialises
# List<SearchResult> as List<LinkedTreeMap> — a ClassCastException in search on
# minified (Play) builds. Keep the classes, their members and signatures.
-keep class com.dharmachakra.fba_android.data.repository.ContentRepository$* { *; }
