plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mediaforge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mediaforge.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("io.coil-kt:coil-gif:2.6.0")
    implementation("io.coil-kt:coil-video:2.6.0")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("com.caverock:androidsvg-aar:1.4") // A20.a: SVG import (rasterised once)
    // A17: FFmpeg engine for video export. Preferred: our own build (app/libs/ffmpeg-kit.aar, made by the
    // "Build FFmpeg engine" workflow: libx264, libx265, libvpx, libass). Fallback: the ffmpegKit property in
    // gradle.properties; blank = the app builds without video export (VideoExporter.available() = false).
    val ownEngine = file("libs/ffmpeg-kit.aar")
    if (ownEngine.exists()) {
        implementation(files(ownEngine))
        implementation("com.arthenica:smart-exception-java:0.2.1") // FFmpegKit's only Java dependency
    } else {
        providers.gradleProperty("ffmpegKit").orNull?.takeIf { it.isNotBlank() }?.let { implementation(it) }
    }
}
