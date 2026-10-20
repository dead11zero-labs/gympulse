plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

android {
    namespace = "app.gympulse.core.health"

    testOptions {
        // Record-Klassen von Health Connect sind reines Kotlin; Android-Stubs liefern Standardwerte.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.health.connect.client)
    // ActivityResultContract für den Berechtigungsdialog ist Teil der öffentlichen API.
    api(libs.androidx.activity)
    implementation(libs.kotlinx.coroutines.core)
}
