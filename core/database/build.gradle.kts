plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

android {
    namespace = "app.gympulse.core.database"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    // Exportierte Schemas sind die Grundlage für Migrationstests und den späteren Server-Sync.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
}
