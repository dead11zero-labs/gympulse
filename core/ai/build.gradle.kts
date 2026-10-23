plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

// Schicht 2 des Plans "Lokale KI": On-Device-LLM (LiteRT-LM, Gemma 4 E2B) als InsightNarrator.
// Das Modell liegt nicht im APK; ModelLocator findet es in filesDir/models/ (Download folgt in PR 5).
android {
    namespace = "app.gympulse.core.ai"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(projects.core.insights)
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
