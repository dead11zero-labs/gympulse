package app.gympulse.feature.workout.timer

import app.gympulse.core.data.util.TimeProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject

/** Emits the current time about once per second, only used to refresh time displays. */
interface Ticker {
    fun ticks(): Flow<Instant>
}

class SystemTicker @Inject constructor(
    private val time: TimeProvider,
) : Ticker {
    override fun ticks(): Flow<Instant> = flow {
        while (true) {
            val now = time.now()
            emit(now)
            // Wake up right after the next full second so displayed seconds change evenly.
            delay(1_000 - now.toEpochMilli() % 1_000)
        }
    }
}
