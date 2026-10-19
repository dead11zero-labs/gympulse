plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.gympulse.core.data"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.database)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}
