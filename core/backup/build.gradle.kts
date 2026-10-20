plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.gympulse.core.backup"

    testOptions {
        // Robolectric braucht die Android-Ressourcen für die In-Memory-Room-Tests.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.core.database)
    implementation(projects.core.settings)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.core)
    // BackupFile legt JsonObject in seiner öffentlichen API offen.
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
