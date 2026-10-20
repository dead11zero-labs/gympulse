plugins {
    alias(libs.plugins.gympulse.android.feature)
}

android {
    namespace = "app.gympulse.feature.settings"
}

dependencies {
    implementation(projects.core.settings)
    implementation(projects.core.data)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
