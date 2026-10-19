package app.gympulse.core.data.util

import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Zeitquelle, in Tests austauschbar. */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}
