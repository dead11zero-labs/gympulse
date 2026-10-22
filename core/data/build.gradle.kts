plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.gympulse.core.data"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    api(projects.core.model)
    api(projects.core.insights)
    implementation(projects.core.database)
    api(projects.core.featuregate)
    implementation(projects.core.settings)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    // In-Memory-Room für Repository-Tests mit echten DAOs.
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.ktx)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
