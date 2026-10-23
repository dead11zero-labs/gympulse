plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

android {
    namespace = "app.gympulse.core.workout"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    // WorkoutSummary exposes WorkoutHealthMetrics, the controller works on core:model types.
    api(projects.core.data)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.database)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
