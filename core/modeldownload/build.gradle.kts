plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

// Download-Manager für das lokale KI-Modell (KI-PR 5): WorkManager-Job nur im WLAN, Resume per
// HTTP-Range, SHA-256-Prüfung. Schnittstellen (ModelLocator, ModelDownloadManager) liegen in core:data.
android {
    namespace = "app.gympulse.core.modeldownload"
}

dependencies {
    implementation(projects.core.data)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    // OkHttp ist über ktor-client-okhttp (core:cloud) und Coil bereits im Projekt.
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver3)
}
