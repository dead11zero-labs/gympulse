package app.gympulse.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import app.gympulse.wear.ui.WearApp
import app.gympulse.wear.workout.WatchWorkoutServiceStarter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var workoutServiceStarter: WatchWorkoutServiceStarter

    // Ohne die Berechtigung läuft der Workout-Dienst trotzdem, nur Notification und Ongoing Activity fehlen.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { WearApp() }
        if (savedInstanceState == null &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        // Im Vordergrund ist der Start immer erlaubt; holt einen im Hintergrund verweigerten Start nach.
        workoutServiceStarter.ensureRunning()
    }
}
