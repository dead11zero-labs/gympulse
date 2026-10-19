import app.gympulse.buildlogic.configureCommon
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Android-App-Modul. Kotlin ist ab AGP 9 eingebaut, ein eigenes Kotlin-Plugin entfällt. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureCommon()
        }
    }
}
