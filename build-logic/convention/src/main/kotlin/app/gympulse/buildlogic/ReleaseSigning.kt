package app.gympulse.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

/**
 * Release-Signing-Konfiguration für alle Apps (Handy und Wear). Beide Apps teilen sich die applicationId
 * und müssen mit demselben Schlüssel signiert sein, sonst funktioniert die Wearable Data Layer nicht.
 * Der Keystore kommt aus Umgebungsvariablen (CI: GitHub Secrets) und liegt nie im Repo.
 * Ohne Keystore entsteht kein "release"-Config, und die Module fallen auf den Debug-Schlüssel zurück.
 */
internal fun ApplicationExtension.configureReleaseSigning(project: Project) {
    val env = project.providers
    val releaseKeystore = env.environmentVariable("GYMPULSE_KEYSTORE_FILE").orNull
    if (releaseKeystore != null) {
        val keystorePassword = env.environmentVariable("GYMPULSE_KEYSTORE_PASSWORD").get()
        val alias = env.environmentVariable("GYMPULSE_KEY_ALIAS").get()
        val aliasPassword = env.environmentVariable("GYMPULSE_KEY_PASSWORD").get()
        signingConfigs.create("release") {
            storeFile = project.file(releaseKeystore)
            storePassword = keystorePassword
            keyAlias = alias
            keyPassword = aliasPassword
        }
    }
}
