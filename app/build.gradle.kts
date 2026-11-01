plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.krishna.aegis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.krishna.aegis"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            "-Xskip-metadata-version-check"
        )
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // 1. Google AI Edge LiteRT-LM for on-device Gemma 4 inference
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.18.0")

    // 2. Vosk Offline Speech Recognition (16kHz Wake-Word Engine)
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    // 3. Room SQLite for Self-Improving Skill Memory & Fact Store
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // 4. Biometric Authentication for Human-in-the-Loop Safety Gate
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // 5. WorkManager for Background Model Weight Delivery
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // 6. Coroutines, JSON, & Compose Setup UI
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.ui:ui:1.7.5")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
}
