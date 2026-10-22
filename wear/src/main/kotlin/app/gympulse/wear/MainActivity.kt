package app.gympulse.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.wear.data.WatchWorkoutRepository
import app.gympulse.wear.data.WearConnection
import app.gympulse.wear.theme.GymPulseWearTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var repository: WatchWorkoutRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GymPulseWearTheme {
                val connection by repository.connection.collectAsStateWithLifecycle()
                HelloScreen(isOffline = connection == WearConnection.DISCONNECTED)
            }
        }
    }
}
