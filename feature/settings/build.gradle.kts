plugins {
    alias(libs.plugins.gympulse.android.feature)
}

android {
    namespace = "app.gympulse.feature.settings"
}

dependencies {
    implementation(projects.core.settings)
    implementation(projects.core.data)
    implementation(projects.core.backup)
    implementation(projects.core.health)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
