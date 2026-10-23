package app.gympulse.wear.workout

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager

/** Haptik bei Pausenende, nur auf der Uhr. Das Telefon vibriert dann nicht (Capability, siehe [RestAlertAdvertiser]). */
internal object RestHaptics {
    /** Drei kurze, kräftige Impulse; am Handgelenk auch mitten im Satz spürbar. */
    private val TIMINGS = longArrayOf(0, 180, 120, 180, 120, 320)
    private val AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255)

    fun vibrate(context: Context) {
        val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(TIMINGS, AMPLITUDES, -1)
        } else {
            VibrationEffect.createWaveform(TIMINGS, -1)
        }
        // Alarm-Nutzung: Ein Pausen-Timer soll auch bei ausgeschaltetem Display durchkommen.
        vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
    }
}
