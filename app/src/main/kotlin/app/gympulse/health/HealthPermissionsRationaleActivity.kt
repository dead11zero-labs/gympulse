package app.gympulse.health

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import app.gympulse.core.designsystem.theme.GymPulseTheme
import app.gympulse.feature.settings.health.HealthPrivacyScreen

/**
 * Datenschutzhinweis für Health Connect. Bis Android 13 startet Health Connect diese Activity über
 * `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`, ab Android 14 über den Alias mit
 * `android.intent.action.VIEW_PERMISSION_USAGE` (siehe AndroidManifest.xml).
 */
class HealthPermissionsRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymPulseTheme {
                HealthPrivacyScreen(onClose = ::finish)
            }
        }
    }
}
