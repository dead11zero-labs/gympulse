plugins {
    alias(libs.plugins.gympulse.android.feature)
}

android {
    namespace = "app.gympulse.feature.plans"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.model)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.kotlinx.coroutines.test)
}
