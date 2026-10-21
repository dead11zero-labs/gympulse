plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.android.compose)
}

android {
    namespace = "app.gympulse.core.designsystem"
}

dependencies {
    // Theme und Icons sind Teil der öffentlichen API des Designsystems.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.core)
    // Farben der adaptiven Navigation (Bar/Rail) liefert das Designsystem.
    api(libs.androidx.compose.material3.navigation.suite)
    // Plus Jakarta Sans und Roboto Flex als Google Downloadable Fonts (Version über die Compose-BOM).
    implementation(libs.androidx.compose.ui.text.google.fonts)
}
