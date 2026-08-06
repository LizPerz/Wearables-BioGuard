# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- DevSecOps / R8 hardening ---

# Mantener la información de línea para trazas legibles (logs de seguridad).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: conservar los serializadores generados y su metadata.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.bioguard_wearos.**$$serializer { *; }
-keepclassmembers class com.example.bioguard_wearos.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.bioguard_wearos.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Evitar que R8 elimine clases referenciadas solo por reflection en Hilt/Room
# (reglas por defecto de AGP cubren la mayoría; estas son salvaguarda adicional).
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }