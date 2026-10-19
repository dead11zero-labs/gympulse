plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

android {
    namespace = "app.gympulse.core.featuregate"
}

dependencies {
    // Flow ist Teil der öffentlichen API (FeatureGate.observe).
    api(libs.kotlinx.coroutines.core)
}
