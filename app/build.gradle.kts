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
        versionCode = 1
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
        }
    }

    androidResources {
        // Nur die unterstützten App-Sprachen ausliefern.
        localeFilters += listOf("en", "de")
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.feature.dashboard)
    implementation(projects.feature.workout)
    implementation(projects.feature.plans)
    implementation(projects.feature.library)
    implementation(projects.feature.calendar)
    implementation(projects.feature.settings)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
