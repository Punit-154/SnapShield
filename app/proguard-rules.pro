# TensorFlow Lite rules
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.**

# Keep TfliteLlmEngine
-keep class com.smssentry.deepcheck.model.TfliteLlmEngine { *; }
-keep class com.smssentry.deepcheck.model.GemmaTokenizer { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.smssentry.**$$serializer { *; }
-keepclassmembers class com.smssentry.** { *** Companion; }
-keepclasseswithmembers class com.smssentry.** { kotlinx.serialization.KSerializer serializer(...); }

# LiteRT-LM
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepclassmembers class * { @androidx.room.* <methods>; }

# Hilt
-keep class dagger.hilt.** { *; }
