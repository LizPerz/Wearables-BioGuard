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
        versionCode = 1
        versionName = "1.0"
        buildConfigField(
            "String",
            "BIOGUARD_PAIRING_SECRET",
            "\"${System.getenv("BIOGUARD_PAIRING_SECRET") ?: "dev-only-change-me-bioguard-pairing-secret-32"}\""
        )
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
            val pairingSecret = System.getenv("BIOGUARD_PAIRING_SECRET")
                ?: if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
                    throw GradleException("BIOGUARD_PAIRING_SECRET is required for release builds")
                } else {
                    "release-secret-not-configured"
                }
            buildConfigField("String", "BIOGUARD_PAIRING_SECRET", "\"$pairingSecret\"")
            isMinifyEnabled = true
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
