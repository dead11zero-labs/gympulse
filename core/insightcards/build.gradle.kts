plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.android.compose)
}

android {
    namespace = "app.gympulse.core.insightcards"
}

// Insight-Karten (Dashboard, Übungsdetail): UI-State-Mapper aus `core:insights` und die
// gemeinsamen Compose-Karten. Strings (DE/EN) liegen ebenfalls hier.
dependencies {
    api(projects.core.insights)
    // Muskelgruppen-Namen (R.string.muscle_*) liegen in core:data.
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
}
