plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

// Telefon-Seite der Wear-OS-Anbindung: Snapshot-Publisher, Kommando-Empfang (WearableListenerService),
// Capability-Prüfung; alles hinter Feature.WEAR_APP.
android {
    namespace = "app.gympulse.core.wearsync"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.workout)
    implementation(projects.core.featuregate)
    implementation(projects.core.wearprotocol)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.kotlinx.coroutines.test)
}
