plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.22" apply false
    id("com.google.dagger.hilt.android") version "2.51" apply false
}

val sdkDir: String? = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
if (sdkDir != null) {
    val localProps = rootProject.file("local.properties")
    localProps.writeText("sdk.dir=${sdkDir.replace("\\", "/")}\n")
}
