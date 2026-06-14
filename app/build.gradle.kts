plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.smssentry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smssentry"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // To sign release builds, create a keystore.properties file in the project root with:
    //   storeFile=path/to/keystore.jks
    //   storePassword=...
    //   keyAlias=...
    //   keyPassword=...
    // Then uncomment the signingConfigs block below.
    // signingConfigs {
    //     create("release") {
    //         val keystorePropertiesFile = rootProject.file("keystore.properties")
    //         val keystoreProperties = java.util.Properties()
    //         if (keystorePropertiesFile.exists()) {
    //             keystoreProperties.load(keystorePropertiesFile.inputStream())
    //         }
    //         storeFile = file(keystoreProperties["storeFile"] as? String ?: "release.jks")
    //         storePassword = keystoreProperties["storePassword"] as? String ?: ""
    //         keyAlias = keystoreProperties["keyAlias"] as? String ?: ""
    //         keyPassword = keystoreProperties["keyPassword"] as? String ?: ""
    //     }
    // }

    buildTypes {
        debug {
            buildConfigField("String", "PROXY_URL", "\"https://smsentry-proxy.joel010-alfred.workers.dev\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "PROXY_URL", "\"https://smsentry-proxy.joel010-alfred.workers.dev\"")
            // signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.8")

    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.14")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    implementation("androidx.datastore:datastore-preferences:1.1.3")

    // LiteRT-LM official library
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.13.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("io.mockk:mockk:1.14.11")
}
