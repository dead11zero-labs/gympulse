plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

android {
    namespace = "app.gympulse.core.settings"
}

dependencies {
    api(projects.core.designsystem)
    // DataStore steckt im Hilt-Modul, daher muss die App-Komponente die Typen sehen.
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
