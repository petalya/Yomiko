buildscript {
    dependencies {
        // classpath(libs.android.shortcut.gradle)
        classpath(sylibs.versionsx)
    }
}

plugins {
    alias(kotlinx.plugins.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.moko) apply false
    alias(libs.plugins.sqldelight) apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
    alias(libs.plugins.googleFirebaseCrashlytics) apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
