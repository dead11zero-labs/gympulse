package app.gympulse.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion

object AndroidConfig {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    // Health Connect ist ab Android 9 (API 28) nutzbar.
    const val MIN_SDK = 28
    val JAVA_VERSION = JavaVersion.VERSION_17
}

internal fun ApplicationExtension.configureCommon() {
    compileSdk = AndroidConfig.COMPILE_SDK
    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        targetSdk = AndroidConfig.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = AndroidConfig.JAVA_VERSION
        targetCompatibility = AndroidConfig.JAVA_VERSION
    }
}

internal fun LibraryExtension.configureCommon() {
    compileSdk = AndroidConfig.COMPILE_SDK
    defaultConfig {
        minSdk = AndroidConfig.MIN_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = AndroidConfig.JAVA_VERSION
        targetCompatibility = AndroidConfig.JAVA_VERSION
    }
}
