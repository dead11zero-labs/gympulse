package app.gympulse.core.data.health

import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.featuregate.Feature
import app.gympulse.core.featuregate.FeatureGate
import app.gympulse.core.featuregate.UserFeatureToggles
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Lesende Health-Connect-Funktionen: Herzfrequenz, Schritte, aktive Kalorien und optional Schlaf.
 * Die Werte stammen von allen Apps, die nach Health Connect schreiben, etwa Samsung Health oder
 * Smartwatch-Apps; GymPulse bindet keine dieser Apps direkt an.
 *
 * Alles läuft nur bei aktivem [Feature.HEALTH_CONNECT], verfügbarem Client und erteilten
 * Kernberechtigungen. Lese-Berechtigungen für Aktivitätsdaten sind optional: fehlen sie, bleiben
 * die jeweiligen Werte leer. Fehler des Dienstes führen nie zu Abbrüchen.
 */
class HealthInsights @Inject constructor(
    private val dataSource: HealthDataSource,
    private val featureGate: FeatureGate,
    private val userToggles: UserFeatureToggles,
    private val time: TimeProvider,
) : WorkoutHealthReader, HealthStepsReader, HealthSleepReader {

    /** Optionale Lese-Berechtigungen für Herzfrequenz, Schritte und aktive Kalorien. */
    val activityPermissions: Set<String>
        get() = ACTIVITY_TYPES.map(dataSource::readPermission).toSet()

    val sleepPermission: String get() = dataSource.readPermission(HealthReadType.SLEEP)

    /** Alles, was beim Einschalten angefragt wird: Kernberechtigungen, Aktivitätsdaten, optional Schlaf. */
    fun permissionsToRequest(includeSleep: Boolean): Set<String> = buildSet {
        addAll(dataSource.requiredPermissions)
        addAll(activityPermissions)
        if (includeSleep) add(sleepPermission)
    }

    fun observeSleepEnabled(): Flow<Boolean> = featureGate.observe(Feature.HEALTH_SLEEP)

    fun setSleepEnabled(enabled: Boolean) = userToggles.setEnabled(Feature.HEALTH_SLEEP, enabled)

    /** Lesbare Datentypen, für die eine Berechtigung erteilt ist; leer, wenn die Anbindung nicht aktiv ist. */
    suspend fun grantedReadTypes(): Set<HealthReadType> = guarded(emptySet()) {
        if (!featureGate.isEnabled(Feature.HEALTH_CONNECT)) return@guarded emptySet()
        if (dataSource.availability() != HealthAvailability.AVAILABLE) return@guarded emptySet()
        val granted = dataSource.grantedPermissions()
        if (!granted.containsAll(dataSource.requiredPermissions)) return@guarded emptySet()
        HealthReadType.entries.filterTo(mutableSetOf()) { dataSource.readPermission(it) in granted }
    }

    /**
     * Werte im Zeitfenster eines beendeten Workouts: Durchschnitts- und Maximalpuls, Schritte,
     * aktive Kalorien und mit Schlaf-Unterschalter die letzte Schlafphase vor dem Start.
     */
    override suspend fun read(start: Instant, end: Instant): WorkoutHealthMetrics? {
        if (!end.isAfter(start)) return null
        val types = readableTypes()
        if (types.isEmpty()) return null

        val activityTypes = types intersect ACTIVITY_TYPES
        val activity = if (activityTypes.isEmpty()) {
            HealthActivity()
        } else {
            guarded(HealthActivity()) { dataSource.aggregateActivity(start, end, activityTypes) }
        }
        val sleep = if (HealthReadType.SLEEP in types) {
            guarded(null) { dataSource.readLatestSleep(start.minus(SLEEP_LOOKBACK), start) }
        } else {
            null
        }
        val sleepMinutes = sleep?.let { Duration.between(it.start, it.end).toMinutes() }?.takeIf { it > 0 }

        val metrics = WorkoutHealthMetrics(
            averageHeartRateBpm = activity.averageHeartRateBpm?.takeIf { it > 0 },
            maxHeartRateBpm = activity.maxHeartRateBpm?.takeIf { it > 0 },
            steps = activity.steps?.takeIf { it > 0 },
            activeCaloriesKcal = activity.activeCaloriesKcal?.takeIf { it >= MIN_CALORIES_KCAL },
            sleepMinutes = sleepMinutes,
        )
        return metrics.takeUnless { it.isEmpty }
    }

    /**
     * Schritte seit Mitternacht und seit Beginn der laufenden Woche (Zeitzone des Geräts). Liefert
     * statt eines Fehlers einen Zustand, wenn Health Connect fehlt, aus ist oder die
     * Schritte-Berechtigung nicht erteilt wurde.
     */
    override suspend fun steps(firstDayOfWeek: DayOfWeek): HealthSteps {
        val availability = guarded(HealthAvailability.UNSUPPORTED) { dataSource.availability() }
        if (availability != HealthAvailability.AVAILABLE) return HealthSteps.Unavailable
        val types = grantedReadTypes()
        if (types.isEmpty()) return HealthSteps.Disabled
        if (HealthReadType.STEPS !in types) return HealthSteps.PermissionMissing

        val now = time.now()
        val today = now.atZone(time.zone()).toLocalDate()
        val dayStart = today.atStartOfDay(time.zone()).toInstant()
        val weekStart = today.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
            .atStartOfDay(time.zone()).toInstant()
        return try {
            HealthSteps.Available(today = stepsBetween(dayStart, now), week = stepsBetween(weekStart, now))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthSteps.Unreadable
        }
    }

    /**
     * Dauer der jüngsten Schlafphase, die in den letzten 24 Stunden endete. Liefert statt eines
     * Fehlers einen Zustand, wenn Health Connect fehlt, aus ist, der Schlaf-Unterschalter aus ist
     * oder die Schlaf-Berechtigung nicht erteilt wurde.
     */
    override suspend fun lastNight(): HealthLastNightSleep {
        val availability = guarded(HealthAvailability.UNSUPPORTED) { dataSource.availability() }
        if (availability != HealthAvailability.AVAILABLE) return HealthLastNightSleep.Unavailable
        val types = grantedReadTypes()
        if (types.isEmpty() || !featureGate.isEnabled(Feature.HEALTH_SLEEP)) return HealthLastNightSleep.Disabled
        if (HealthReadType.SLEEP !in types) return HealthLastNightSleep.PermissionMissing

        val now = time.now()
        return try {
            val sleep = dataSource.readLatestSleep(now.minus(LAST_NIGHT_LOOKBACK), now)
            val minutes = sleep?.let { Duration.between(it.start, it.end).toMinutes() }
            if (minutes != null && minutes > 0) HealthLastNightSleep.Available(minutes) else HealthLastNightSleep.NoData
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            HealthLastNightSleep.Unreadable
        }
    }

    private suspend fun stepsBetween(start: Instant, end: Instant): Long {
        if (!end.isAfter(start)) return 0
        return dataSource.aggregateActivity(start, end, setOf(HealthReadType.STEPS)).steps ?: 0
    }

    /** Erteilte Lesetypen; Schlaf nur mit eingeschaltetem Unterschalter. */
    private suspend fun readableTypes(): Set<HealthReadType> {
        val granted = grantedReadTypes()
        return if (featureGate.isEnabled(Feature.HEALTH_SLEEP)) granted else granted - HealthReadType.SLEEP
    }

    private inline fun <T> guarded(fallback: T, block: () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Health Connect kann jederzeit Berechtigungen entziehen oder Anfragen drosseln.
        fallback
    }

    companion object {
        val ACTIVITY_TYPES: Set<HealthReadType> =
            setOf(HealthReadType.HEART_RATE, HealthReadType.STEPS, HealthReadType.ACTIVE_CALORIES)

        /** Schlaf, der bis zu so lange vor dem Workout endet, zählt als "letzte Nacht". */
        val SLEEP_LOOKBACK: Duration = Duration.ofHours(18)

        /** Für die Dashboard-Kachel zählt Schlaf, der in den letzten 24 Stunden endete, als "letzte Nacht". */
        val LAST_NIGHT_LOOKBACK: Duration = Duration.ofHours(24)

        private const val MIN_CALORIES_KCAL = 1.0
    }
}
