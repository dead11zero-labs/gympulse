package app.gympulse.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.gympulse.wear.ui.WearApp
import app.gympulse.wear.workout.HeartRatePermission
import app.gympulse.wear.workout.HeartRateRecording
import app.gympulse.wear.workout.WatchWorkoutServiceStarter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var workoutServiceStarter: WatchWorkoutServiceStarter

    @Inject lateinit var heartRateRecording: HeartRateRecording

    // Ohne Benachrichtigungs-Berechtigung läuft der Workout-Dienst trotzdem, nur Notification und Ongoing
    // Activity fehlen. Ohne Puls-Berechtigung läuft das Workout ohne Puls; der Screen zeigt einen Hinweis.
    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        // Frisch erteilte Puls-Berechtigung: eine laufende Session fängt jetzt an zu messen.
        heartRateRecording.resume()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
        if (savedInstanceState == null) {
            val missing = listOf(Manifest.permission.POST_NOTIFICATIONS, HeartRatePermission.requiredFor(Build.VERSION.SDK_INT))
                .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
        }
    }

    override fun onStart() {
        super.onStart()
        // Im Vordergrund ist der Start immer erlaubt; holt einen im Hintergrund verweigerten Start nach.
        workoutServiceStarter.ensureRunning()
        heartRateRecording.resume()
    }
}
