# ── TensorFlow Lite ────────────────────────────────────────────────
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# Keep legacy TfliteLlmEngine (may be referenced by reflection)
-keep class com.smssentry.deepcheck.model.TfliteLlmEngine { *; }
-keep class com.smssentry.deepcheck.model.GemmaTokenizer { *; }

# ── LiteRT-LM ─────────────────────────────────────────────────────
-keep class com.google.ai.edge.litertlm.** { *; }
-keepclassmembers class com.google.ai.edge.litertlm.** {
    native <methods>;
}
-dontwarn com.google.ai.edge.litertlm.**

# ── Kotlin serialization ──────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.smssentry.**$$serializer { *; }
-keepclassmembers class com.smssentry.** { *** Companion; }
-keepclasseswithmembers class com.smssentry.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep @Serializable data classes used by the proxy / verdict parser
-keep @kotlinx.serialization.Serializable class com.smssentry.** { *; }

# ── Room ───────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}

# ── Hilt / Dagger ─────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class dagger.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
# Hilt-generated component classes
-keep class **_HiltModules* { *; }
-keep class **_GeneratedInjector { *; }
-dontwarn dagger.hilt.internal.**

# ── OkHttp ─────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Kotlin Coroutines ──────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }

# ── App model classes (prevent stripping of data classes) ──────────
-keep class com.smssentry.deepcheck.model.LlmResponse$** { *; }
-keep class com.smssentry.deepcheck.model.VerdictJson { *; }
-keep class com.smssentry.deepcheck.proxy.WhoisResult { *; }
