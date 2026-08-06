// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.dependencycheck)
}

// DevSecOps - Software Composition Analysis (SCA).
// Escanea todas las dependencias del proyecto contra la base de CVEs de NVD.
// Ejecución: ./gradlew dependencyCheckAnalyze  (reporte en build/reports/dependency-check)
dependencyCheck {
    // Falla el build solo ante vulnerabilidades CRÍTICAS (CVSS >= 9.0) de forma
    // predeterminada. Puede endurecerse a 8.0 cuando el equipo lo asuma.
    failBuildOnCVSS = 9.0f
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