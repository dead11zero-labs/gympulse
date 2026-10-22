plugins {
    alias(libs.plugins.gympulse.android.application)
    alias(libs.plugins.gympulse.android.compose)
    alias(libs.plugins.gympulse.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.gympulse"

    defaultConfig {
        applicationId = "app.gympulse"
        versionCode = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull() ?: 1
        versionName = "0.1.0"
    }

    // Die Release-Signatur (Keystore aus Umgebungsvariablen) setzt das Convention-Plugin, siehe ReleaseSigning.kt.
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    androidResources {
        // Nur die unterstützten App-Sprachen ausliefern.
        localeFilters += listOf("en", "de")
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.data)
    implementation(projects.core.database)
    implementation(projects.core.featuregate)
    implementation(projects.core.settings)
    implementation(projects.core.health)
    implementation(projects.core.cloud)
    implementation(projects.feature.dashboard)
    implementation(projects.feature.workout)
    implementation(projects.feature.plans)
    implementation(projects.feature.library)
    implementation(projects.feature.calendar)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // AppCompatActivity und AppCompatDelegate für die App-Sprache (Per-App-Locales ab API 28).
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
