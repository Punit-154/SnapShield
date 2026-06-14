import java.util.Properties

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.2.10-2.0.2" apply false
}

val sdkDir: String? = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
if (sdkDir != null) {
    val localPropsFile = rootProject.file("local.properties")
    val properties = Properties()
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { properties.load(it) }
    }
    properties.setProperty("sdk.dir", sdkDir)
    localPropsFile.outputStream().use { 
        properties.store(it, "Auto-generated SDK path")
    }
}
