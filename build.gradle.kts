// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.dependencycheck)
    alias(libs.plugins.cyclonedxBom) apply false
}

// DevSecOps - Software Composition Analysis (SCA).
// Escanea todas las dependencias del proyecto contra la base de CVEs de NVD.
// Ejecución: ./gradlew dependencyCheckAnalyze  (reporte en build/reports/dependency-check)
dependencyCheck {
    // Falla el build ante vulnerabilidades ALTAS (CVSS >= 8.0). Si la NVD
    // reporta falso-positivos, documentar la exención en suppressionFiles.
    failBuildOnCVSS = 8.0f
    // Configuración de falso-positivos / exenciones documentadas.
    suppressionFiles = listOf("config/dependency-check/suppressions.xml")
    formats = listOf("HTML", "JSON")

    val nvdApiKey = providers.gradleProperty("NVD_API_KEY").orNull
        ?: System.getenv("NVD_API_KEY")
    if (!nvdApiKey.isNullOrEmpty()) {
        nvd {
            apiKey = nvdApiKey
        }
    }
}