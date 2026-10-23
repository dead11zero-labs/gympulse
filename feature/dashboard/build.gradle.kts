plugins {
    alias(libs.plugins.gympulse.android.feature)
}

android {
    namespace = "app.gympulse.feature.dashboard"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.insightcards)
    implementation(projects.core.featuregate)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
