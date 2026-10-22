package app.gympulse.feature.workout.service

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Short double vibration on the phone when a rest period ends. */
internal object RestAlarm {
    private val PATTERN = longArrayOf(0, 300, 150, 300)

    fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        vibrator.vibrate(VibrationEffect.createWaveform(PATTERN, -1))
    }
}
