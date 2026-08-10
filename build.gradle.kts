// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("org.cyclonedx.bom") version "3.3.0"
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}

group = "com.bioguard"
version = "1.0.0"
