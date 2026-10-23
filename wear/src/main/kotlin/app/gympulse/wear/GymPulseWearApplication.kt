package app.gympulse.wear

import android.app.Application
import app.gympulse.wear.workout.HeartRateRecording
import app.gympulse.wear.workout.WatchWorkoutServiceStarter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GymPulseWearApplication : Application() {
    @Inject lateinit var workoutServiceStarter: WatchWorkoutServiceStarter

    @Inject lateinit var heartRateRecording: HeartRateRecording

    override fun onCreate() {
        super.onCreate()
        // Auch wenn erst der WorkoutDataListenerService den Prozess geweckt hat.
        workoutServiceStarter.start()
        heartRateRecording.start()
    }
}
