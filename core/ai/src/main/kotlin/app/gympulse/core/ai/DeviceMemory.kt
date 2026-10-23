package app.gympulse.core.ai

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Arbeitsspeicher des Geräts, als Interface für Tests. */
interface DeviceMemory {
    val totalBytes: Long
    val availableBytes: Long

    /** Das System meldet knappen Speicher. */
    val isLow: Boolean
}

class ActivityManagerDeviceMemory @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceMemory {

    private fun info(): ActivityManager.MemoryInfo {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return info
    }

    override val totalBytes: Long get() = info().totalMem
    override val availableBytes: Long get() = info().availMem
    override val isLow: Boolean get() = info().lowMemory
}
