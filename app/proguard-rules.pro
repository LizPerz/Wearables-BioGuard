# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep line numbers for readable stack traces in production (Firebase Crashlytics).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────
# Hilt / Dagger
# ─────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ─────────────────────────────────────────────
# kotlinx.serialization
# ─────────────────────────────────────────────
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    static *** INSTANCE;
}
-keep,includedescriptorclasses class com.example.bioguard_wearos.**$$serializer { *; }
-keepclassmembers class com.example.bioguard_wearos.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.bioguard_wearos.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─────────────────────────────────────────────
# Room
# ─────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# ─────────────────────────────────────────────
# WorkManager
# ─────────────────────────────────────────────
-dontwarn com.google.common.**
-dontwarn org.checkerframework.**
-dontwarn javax.annotation.**

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}