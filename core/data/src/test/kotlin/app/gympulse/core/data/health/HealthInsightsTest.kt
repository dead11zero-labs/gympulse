package app.gympulse.core.data.health

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class HealthInsightsTest {

    /** A Thursday. */
    private val now = Instant.parse("2026-07-02T10:00:00Z")
    private val start = Instant.parse("2026-07-01T16:00:00Z")
    private val end = Instant.parse("2026-07-01T17:00:00Z")

    private val toggles = FakeToggles()
    private val gate = FakeGate(toggles)
    private val source = FakeHealthDataSource()
    private val insights = HealthInsights(
        dataSource = source,
        featureGate = gate,
        userToggles = toggles,
        time = object : TimeProvider {
            override fun now(): Instant = now
            override fun zone(): ZoneId = ZoneOffset.UTC
        },
    )

    private fun activate(vararg extra: HealthReadType) {
        toggles.setEnabled(Feature.HEALTH_CONNECT, true)
        source.granted = source.requiredPermissions + extra.map(source::readPermission)
    }

    @Test
    fun grantedReadTypes_emptyWhenUnavailableDisabledOrCoreMissing() = runTest {
        source.granted = source.requiredPermissions + source.readPermission(HealthReadType.STEPS)
        assertEquals(emptySet<HealthReadType>(), insights.grantedReadTypes())

        toggles.setEnabled(Feature.HEALTH_CONNECT, true)
        source.currentAvailability = HealthAvailability.NOT_INSTALLED
        assertEquals(emptySet<HealthReadType>(), insights.grantedReadTypes())

        source.currentAvailability = HealthAvailability.AVAILABLE
        source.granted = setOf(source.readPermission(HealthReadType.STEPS))
        assertEquals(emptySet<HealthReadType>(), insights.grantedReadTypes())
    }

    @Test
    fun grantedReadTypes_listsGrantedPermissions() = runTest {
        activate(HealthReadType.STEPS)
        assertEquals(setOf(HealthReadType.WEIGHT, HealthReadType.STEPS), insights.grantedReadTypes())
    }

    @Test
    fun sleepIsOnlyReadWithSubToggle() = runTest {
        activate(HealthReadType.SLEEP)
        source.sleep = HealthSleep(start.minus(Duration.ofHours(10)), start.minus(Duration.ofHours(2)))

        assertNull(insights.read(start, end))
        assertFalse(source.sleepRead)

        toggles.setEnabled(Feature.HEALTH_SLEEP, true)
        assertEquals(480L, insights.read(start, end)?.sleepMinutes)
    }

    @Test
    fun read_returnsNullWhenInactive() = runTest {
        source.activity = HealthActivity(averageHeartRateBpm = 120)
        assertNull(insights.read(start, end))
    }

    @Test
    fun read_onlyAggregatesGrantedTypes() = runTest {
        activate(HealthReadType.HEART_RATE)
        source.activity = HealthActivity(averageHeartRateBpm = 128, maxHeartRateBpm = 165)

        val metrics = insights.read(start, end)

        assertEquals(setOf(HealthReadType.HEART_RATE), source.lastAggregateTypes)
        assertEquals(128L, metrics?.averageHeartRateBpm)
        assertEquals(165L, metrics?.maxHeartRateBpm)
        assertEquals(start to end, source.lastAggregateWindow)
    }

    @Test
    fun read_dropsEmptyValues() = runTest {
        activate(HealthReadType.HEART_RATE, HealthReadType.STEPS, HealthReadType.ACTIVE_CALORIES)
        source.activity = HealthActivity(
            averageHeartRateBpm = 0,
            steps = 2_340,
            activeCaloriesKcal = 0.2,
        )

        val metrics = insights.read(start, end)!!

        assertNull(metrics.averageHeartRateBpm)
        assertNull(metrics.activeCaloriesKcal)
        assertEquals(2_340L, metrics.steps)
    }

    @Test
    fun read_returnsNullWithoutData() = runTest {
        activate(HealthReadType.HEART_RATE, HealthReadType.STEPS)
        assertNull(insights.read(start, end))
    }

    @Test
    fun read_ignoresInvalidWindowAndFailures() = runTest {
        activate(HealthReadType.STEPS)
        source.activity = HealthActivity(steps = 100)
        assertNull(insights.read(end, start))

        source.fail = true
        assertNull(insights.read(start, end))
    }

    @Test
    fun steps_reportsStateInsteadOfFailing() = runTest {
        source.currentAvailability = HealthAvailability.NOT_INSTALLED
        assertEquals(HealthSteps.Unavailable, insights.steps(DayOfWeek.MONDAY))

        source.currentAvailability = HealthAvailability.AVAILABLE
        assertEquals(HealthSteps.Disabled, insights.steps(DayOfWeek.MONDAY))

        activate(HealthReadType.HEART_RATE)
        assertEquals(HealthSteps.PermissionMissing, insights.steps(DayOfWeek.MONDAY))

        activate(HealthReadType.STEPS)
        source.fail = true
        assertEquals(HealthSteps.Unreadable, insights.steps(DayOfWeek.MONDAY))
    }

    @Test
    fun steps_aggregatesTodayAndCurrentWeek() = runTest {
        activate(HealthReadType.STEPS)
        source.stepsByStart = mapOf(
            Instant.parse("2026-07-02T00:00:00Z") to 4_200L,
            Instant.parse("2026-06-29T00:00:00Z") to 31_000L,
        )

        assertEquals(HealthSteps.Available(today = 4_200, week = 31_000), insights.steps(DayOfWeek.MONDAY))
        assertEquals(setOf(HealthReadType.STEPS), source.lastAggregateTypes)
        assertEquals(now, source.lastAggregateWindow?.second)

        // Week starting on Sunday begins on 2026-06-28; no data there counts as zero.
        assertEquals(HealthSteps.Available(today = 4_200, week = 0), insights.steps(DayOfWeek.SUNDAY))
    }

    @Test
    fun permissionsToRequest_includesSleepOnlyOnRequest() {
        val withoutSleep = insights.permissionsToRequest(includeSleep = false)
        assertTrue(withoutSleep.containsAll(source.requiredPermissions))
        assertTrue(withoutSleep.containsAll(insights.activityPermissions))
        assertFalse(insights.sleepPermission in withoutSleep)
        assertTrue(insights.sleepPermission in insights.permissionsToRequest(includeSleep = true))
    }

    private class FakeHealthDataSource : HealthDataSource {
        var currentAvailability = HealthAvailability.AVAILABLE
        var granted: Set<String> = emptySet()
        var activity = HealthActivity()
        var sleep: HealthSleep? = null
        var sleepRead = false
        var stepsByStart: Map<Instant, Long>? = null
        var fail = false
        var lastAggregateTypes: Set<HealthReadType>? = null
        var lastAggregateWindow: Pair<Instant, Instant>? = null

        override val requiredPermissions = setOf("write.exercise", "read.weight")

        override fun readPermission(type: HealthReadType): String =
            if (type == HealthReadType.WEIGHT) "read.weight" else "read.${type.name.lowercase()}"

        override fun availability() = currentAvailability

        override suspend fun grantedPermissions() = granted

        override suspend fun insertWorkout(workout: HealthWorkout) = Unit

        override suspend fun readLatestWeight(since: Instant): HealthWeight? = null

        override suspend fun aggregateActivity(
            start: Instant,
            end: Instant,
            types: Set<HealthReadType>,
        ): HealthActivity {
            if (fail) error("throttled")
            lastAggregateTypes = types
            lastAggregateWindow = start to end
            stepsByStart?.let { return HealthActivity(steps = it[start]) }
            return activity
        }

        override suspend fun readLatestSleep(from: Instant, to: Instant): HealthSleep? {
            if (fail) error("throttled")
            sleepRead = true
            return sleep
        }
    }

    private class FakeToggles : UserFeatureToggles {
        val values = MutableStateFlow<Map<Feature, Boolean>>(emptyMap())
        override fun current(feature: Feature): Boolean? = values.value[feature]
        override fun observe(feature: Feature): Flow<Boolean?> = values.map { it[feature] }
        override fun setEnabled(feature: Feature, enabled: Boolean) {
            values.value = values.value + (feature to enabled)
        }
    }

    private class FakeGate(private val toggles: FakeToggles) : FeatureGate {
        override fun isEnabled(feature: Feature): Boolean = toggles.current(feature) ?: feature.enabledByDefault
        override fun observe(feature: Feature): Flow<Boolean> = toggles.observe(feature).map { it ?: feature.enabledByDefault }
    }
}
