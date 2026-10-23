plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

// Schicht 2 des Plans "Lokale KI": On-Device-LLM (LiteRT-LM, Gemma 4 E2B) als InsightNarrator.
// Das Modell liegt nicht im APK; den Pfad liefert der ModelLocator aus core:data (Download: core:modeldownload).
android {
    namespace = "app.gympulse.core.ai"

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(projects.core.insights)
    // ModelLocator-Schnittstelle des Download-Managers (KI-PR 5).
    implementation(projects.core.data)
    implementation(libs.litertlm.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
