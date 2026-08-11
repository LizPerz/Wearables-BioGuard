plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.cyclonedxBom)
}

import java.io.FileInputStream
import java.util.Properties

// DevSecOps: credenciales de firma de release. Se cargan desde keystore.properties
// (local, NO versionado) o desde variables de entorno (usadas en CI).
val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}
// DevSecOps: tratar valores vacíos como "secreto ausente". Evita que un env
// vacío en CI (p.ej. BIOGUARD_STORE_FILE="") se interprete como configurado.
fun secretFromEnvOrProperty(envName: String, propertyName: String): String? =
    (System.getenv(envName) ?: keystoreProperties.getProperty(propertyName))
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.example.bioguard_wearos"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.bioguard_wearos"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Endpoint único de la API. No es un secreto: se mantiene en BuildConfig
        // para separarlo de la lógica de negocio.
        buildConfigField(
            "String",
            "BASE_URL",
            "\"https://bioguard-api-lkvnq.ondigitalocean.app\""
        )
    }

    signingConfigs {
        create("release") {
            storeFile = secretFromEnvOrProperty("BIOGUARD_STORE_FILE", "storeFile")
                ?.let { file(it) }
            storePassword = secretFromEnvOrProperty("BIOGUARD_STORE_PASSWORD", "storePassword")
            keyAlias = secretFromEnvOrProperty("BIOGUARD_KEY_ALIAS", "keyAlias")
            keyPassword = secretFromEnvOrProperty("BIOGUARD_KEY_PASSWORD", "keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            val releaseSigning = signingConfigs.findByName("release")
            if (releaseSigning?.storeFile != null &&
                releaseSigning.keyAlias != null &&
                releaseSigning.keyPassword != null &&
                releaseSigning.storePassword != null
            ) {
                signingConfig = releaseSigning
            } else {
                println(
                    "BioGuard: signing de release no configurado " +
                        "(falta keystore.properties o env BIOGUARD_*). " +
                        "La APK release quedará SIN firmar."
                )
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

// DevSecOps: exportar el schema de Room a version control para auditar y
// generar migraciones seguras entre versiones de la BD cifrada.
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
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.workmanager)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DevSecOps: cifrado de la BD Room en repositorio (SQLCipher).
    implementation(libs.sqlcipher.android)
    implementation(libs.sqlite.androidx)

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