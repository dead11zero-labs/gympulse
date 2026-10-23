package app.gympulse.wear.workout

import app.gympulse.core.wearprotocol.Discard
import app.gympulse.core.wearprotocol.Finish
import app.gympulse.core.wearprotocol.SessionSnapshot
import app.gympulse.core.wearprotocol.WatchWorkoutState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeHeartRateMonitor : HeartRateMonitor {
    override val reading = MutableStateFlow<HeartRateReading>(HeartRateReading.Off)
    var startResult = true
    var starts = 0
    var stops = 0
    private var sequence = 0L

    override suspend fun start(): Boolean {
        starts += 1
        if (startResult) reading.value = HeartRateReading.Searching
        return startResult
    }

    override fun stop() {
        stops += 1
        reading.value = HeartRateReading.Off
    }

    fun beat(bpm: Int) {
        reading.value = HeartRateReading.Bpm(bpm, ++sequence)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHeartRateRecorderTest {
    private val monitor = FakeHeartRateMonitor()
    private val delivered = mutableListOf<Pair<String, HeartRateStats>>()
    private val recorder = WorkoutHeartRateRecorder(monitor, { id, stats -> delivered += id to stats })
    private val states = MutableStateFlow(WatchWorkoutState())

    private fun running(id: String) = WatchWorkoutState(session = SessionSnapshot(sessionId = id, startedAt = 0))

    private suspend fun TestScope.measure(vararg values: Int) {
        for (bpm in values) {
            monitor.beat(bpm)
            advanceUntilIdle()
        }
    }

    private fun TestScope.launchRecorder() = backgroundScope.launch { recorder.run(states) }

    @Test
    fun finishedWorkout_sendsAverageAndMaxAfterTheEnd() = runTest {
        launchRecorder()
        advanceUntilIdle()

        states.value = running("s1")
        advanceUntilIdle()
        assertEquals(1, monitor.starts)

        measure(100, 110, 120, 130, 140, 150, 160, 170, 100, 120)
        // Während des Workouts geht nichts ans Telefon.
        assertTrue(delivered.isEmpty())

        states.value = WatchWorkoutState(pending = listOf(Finish("c1", 1, "s1")))
        advanceUntilIdle()

        assertEquals(1, monitor.stops)
        assertEquals(listOf("s1" to HeartRateStats(averageBpm = 130, maxBpm = 170, sampleCount = 10)), delivered)
    }

    @Test
    fun endedOnPhone_alsoSendsSummary() = runTest {
        launchRecorder()
        states.value = running("s1")
        advanceUntilIdle()
        measure(*IntArray(12) { 120 })

        // Das Telefon beendet: Snapshot ohne Session, kein eigenes Kommando der Uhr.
        states.value = WatchWorkoutState()
        advanceUntilIdle()

        assertEquals(1, delivered.size)
        assertEquals("s1", delivered.single().first)
    }

    @Test
    fun discardedOnWatch_sendsNothing() = runTest {
        launchRecorder()
        states.value = running("s1")
        advanceUntilIdle()
        measure(*IntArray(12) { 120 })

        states.value = WatchWorkoutState(pending = listOf(Discard("c1", 1, "s1")))
        advanceUntilIdle()

        assertEquals(1, monitor.stops)
        assertTrue(delivered.isEmpty())
    }

    @Test
    fun tooFewSamples_sendsNothing() = runTest {
        launchRecorder()
        states.value = running("s1")
        advanceUntilIdle()
        measure(120, 121, 122)

        states.value = WatchWorkoutState()
        advanceUntilIdle()

        assertTrue(delivered.isEmpty())
    }

    @Test
    fun samplesOutsideAWorkout_areIgnored() = runTest {
        launchRecorder()
        advanceUntilIdle()
        measure(*IntArray(12) { 150 })

        states.value = running("s1")
        advanceUntilIdle()
        measure(*IntArray(10) { 100 })
        states.value = WatchWorkoutState()
        advanceUntilIdle()

        assertEquals(HeartRateStats(averageBpm = 100, maxBpm = 100, sampleCount = 10), delivered.single().second)
    }

    @Test
    fun nextWorkout_startsFresh() = runTest {
        launchRecorder()
        states.value = running("s1")
        advanceUntilIdle()
        measure(*IntArray(10) { 160 })
        states.value = WatchWorkoutState()
        advanceUntilIdle()

        states.value = running("s2")
        advanceUntilIdle()
        measure(*IntArray(10) { 90 })
        states.value = WatchWorkoutState()
        advanceUntilIdle()

        assertEquals(listOf("s1", "s2"), delivered.map { it.first })
        assertEquals(160, delivered[0].second.averageBpm)
        assertEquals(90, delivered[1].second.averageBpm)
        assertEquals(2, monitor.starts)
    }

    @Test
    fun monitorCannotStart_workoutContinuesWithoutSummary_andResumeRetries() = runTest {
        monitor.startResult = false
        launchRecorder()
        states.value = running("s1")
        advanceUntilIdle()
        assertEquals(1, monitor.starts)

        // Berechtigung kommt später.
        monitor.startResult = true
        recorder.resume()
        assertEquals(2, monitor.starts)
        measure(*IntArray(10) { 110 })
        states.value = WatchWorkoutState()
        advanceUntilIdle()

        assertEquals(110, delivered.single().second.averageBpm)
    }

    @Test
    fun resumeWithoutSession_doesNothing() = runTest {
        launchRecorder()
        advanceUntilIdle()

        recorder.resume()

        assertEquals(0, monitor.starts)
    }
}
