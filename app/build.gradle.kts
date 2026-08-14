plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
}

import java.util.Properties

val keystoreProperties = Properties().apply {
    val keystoreFile = rootProject.file("keystore.properties")
    if (keystoreFile.exists()) {
        keystoreFile.inputStream().use { load(it) }
    }
    System.getenv("BIOGUARD_WEAR_STORE_FILE")?.let { setProperty("storeFile", it) }
    System.getenv("BIOGUARD_WEAR_STORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("BIOGUARD_WEAR_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("BIOGUARD_WEAR_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
    System.getenv("BIOGUARD_STORE_FILE")?.let { setProperty("storeFile", it) }
    System.getenv("BIOGUARD_STORE_PASSWORD")?.let { setProperty("storePassword", it) }
    System.getenv("BIOGUARD_KEY_ALIAS")?.let { setProperty("keyAlias", it) }
    System.getenv("BIOGUARD_KEY_PASSWORD")?.let { setProperty("keyPassword", it) }
}

val releaseRequested = gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }
val releaseVersionCode = System.getenv("BIOGUARD_VERSION_CODE")?.toIntOrNull() ?: 1
val releaseVersionName = System.getenv("BIOGUARD_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "1.0"
if (releaseRequested) {
    val requiredSigningKeys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    check(requiredSigningKeys.all { !keystoreProperties.getProperty(it).isNullOrBlank() }) {
        "Release builds require the shared BioGuard signing keystore configuration"
    }
}

android {
    namespace = "com.example.bioguard_wearos"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Wear OS Data Layer pairs apps by application identity and signing certificate.
        // Keep this aligned with the mobile applicationId; Kotlin packages may remain separate.
        applicationId = "com.bioguard.movil"
        minSdk = 30
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
    }

    signingConfigs {
        if (keystoreProperties["storeFile"] != null) {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    useLibrary("wear-sdk")
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling)
    implementation(libs.core.splashscreen)
    implementation(libs.play.services.wearable)
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.wear.tooling.preview)
    implementation(libs.wear.watchface.complications.datasource)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.health.services.client)
    implementation(libs.guava)
    implementation(libs.material.icons.extended)
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.workmanager)
    implementation(libs.zxing.core)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)

    // Samsung Health Sensor SDK (samsung-health-sensor-api.aar) — temperatura de piel real
    // en Galaxy Watch5+. El AAR se descarga del portal de Samsung y se coloca en app/libs/.
    implementation(fileTree("libs") { include("*.aar") })

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.test.manifest)
    debugImplementation(libs.ui.tooling)
}
