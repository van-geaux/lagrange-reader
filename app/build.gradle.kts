import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

apply(plugin = "kotlin-kapt")

val releaseKeystorePropertiesFile = rootProject.file("keystore.properties")

val releaseKeystoreProperties = Properties().apply {
    if (releaseKeystorePropertiesFile.exists()) {
        load(releaseKeystorePropertiesFile.inputStream())
    }
}

android {
    namespace = "com.bookorbit.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bookorbit.android"
        minSdk = 26
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Release marker: update versionCode and versionName together for every distributed build.
        versionCode = 14
        versionName = "1.2.2"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePropertiesFile.exists()) {
                storeFile = rootProject.file(releaseKeystoreProperties["storeFile"] as String)
                storePassword = releaseKeystoreProperties["storePassword"] as String
                keyAlias = releaseKeystoreProperties["keyAlias"] as String
                keyPassword = releaseKeystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

}

val copyDebugApkWithTimestamp by tasks.registering {
    dependsOn("packageDebug")

    doLast {
        val sourceApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        check(sourceApk.isFile) { "Expected debug APK was not produced: ${sourceApk.absolutePath}" }

        val timestamp = SimpleDateFormat("yyyyMMddHHmm", Locale.ROOT).format(Date())
        val timestampedApk = sourceApk.parentFile.resolve("Lagrange-debug-$timestamp.apk")
        sourceApk.copyTo(timestampedApk, overwrite = true)
        logger.lifecycle("Timestamped debug APK: ${timestampedApk.absolutePath}")
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy(copyDebugApkWithTimestamp)
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation("org.readium.kotlin-toolkit:readium-shared:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-navigator-media-audio:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-adapter-exoplayer-audio:3.0.2")
    implementation("org.readium.kotlin-toolkit:readium-adapter-pdfium:3.0.2")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.compose.material3:material3")
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.24.0")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    add("kapt", "androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
