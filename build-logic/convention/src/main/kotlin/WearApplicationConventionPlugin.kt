import app.gympulse.buildlogic.AndroidConfig
import app.gympulse.buildlogic.configureCommon
import app.gympulse.buildlogic.configureReleaseSigning
import app.gympulse.buildlogic.lib
import app.gympulse.buildlogic.libs
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Wear-OS-App-Modul: Application + Compose for Wear OS (Material 3 für Wear).
 * Bewusst ohne das Phone-Compose-Plugin, weil dessen Material3-Abhängigkeit auf der Uhr nichts verloren hat.
 */
class WearApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<ApplicationExtension> {
            configureCommon()
            configureReleaseSigning(target)
            defaultConfig.minSdk = AndroidConfig.WEAR_MIN_SDK
            buildFeatures.compose = true
        }
        dependencies {
            val bom = platform(libs.lib("androidx-compose-bom"))
            add("implementation", bom)
            add("implementation", libs.lib("androidx-compose-ui"))
            add("implementation", libs.lib("androidx-compose-ui-graphics"))
            add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
            add("implementation", libs.lib("androidx-wear-compose-material3"))
            add("implementation", libs.lib("androidx-wear-compose-foundation"))
            add("debugImplementation", libs.lib("androidx-wear-compose-ui-tooling"))
            add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        }
    }
}
