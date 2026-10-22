plugins {
    alias(libs.plugins.gympulse.wear.application)
    alias(libs.plugins.gympulse.hilt)
}

android {
    namespace = "app.gympulse.wear"

    defaultConfig {
        // Gleiche applicationId wie die Handy-App: Data Layer und Play-Listing brauchen das.
        applicationId = "app.gympulse"
        // Eigenes versionCode-Schema: fester Offset, damit der Code über alle Formfaktoren eindeutig bleibt.
        versionCode = 1_000_000 + (providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1)
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Gleicher Schlüssel wie :app (Convention-Plugin), sonst funktioniert die Data Layer nicht.
            // Ohne Keystore Fallback auf den Debug-Schlüssel, damit der Build installierbar bleibt.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    androidResources {
        localeFilters += listOf("en", "de")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.wearable)
    implementation(project(":core:wearprotocol"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
