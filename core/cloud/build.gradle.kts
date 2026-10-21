plugins {
    alias(libs.plugins.gympulse.android.library)
    alias(libs.plugins.gympulse.hilt)
}

/** Wert aus gradle.properties (oder -P / ORG_GRADLE_PROJECT_<name>) als Java-String-Literal. */
fun buildConfigString(property: String): String {
    val value = providers.gradleProperty(property).orElse("").get().trim()
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}

android {
    namespace = "app.gympulse.core.cloud"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Öffentliche Werte: Projekt-URL und Publishable Key. Niemals service_role-Keys verwenden.
        buildConfigField("String", "SUPABASE_URL", buildConfigString("gympulseSupabaseUrl"))
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", buildConfigString("gympulseSupabasePublishableKey"))
        // OAuth-Client-ID vom Typ "Webanwendung"; leer = Google-Anmeldung deaktiviert.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", buildConfigString("gympulseGoogleWebClientId"))
    }
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.backup)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
