package app.gympulse.core.data.health

import java.time.DayOfWeek
import java.time.Instant

/**
 * Zugriff auf Health Connect. Die Android-Implementierung liegt in `core:health`; hier steht nur
 * die Schnittstelle, damit Logik und Tests ohne Android-Laufzeit auskommen.
 */
interface HealthDataSource {
    /** Berechtigungen (Health-Connect-Strings), die die App für Export und Gewichtsimport braucht. */
    val requiredPermissions: Set<String>

    /** Lese-Berechtigung (Health-Connect-String) für [type]. */
    fun readPermission(type: HealthReadType): String

    fun availability(): HealthAvailability

    /** Bereits erteilte Berechtigungen; leer, wenn Health Connect nicht verfügbar ist. */
    suspend fun grantedPermissions(): Set<String>

    /** Schreibt eine Trainingseinheit. Wirft bei fehlender Berechtigung oder Fehlern des Dienstes. */
    suspend fun insertWorkout(workout: HealthWorkout)

    /** Jüngstes Körpergewicht seit [since] oder null. */
    suspend fun readLatestWeight(since: Instant): HealthWeight?

    /**
     * Aggregiert Herzfrequenz, Schritte und aktive Kalorien zwischen [start] und [end]. Gelesen
     * werden nur die Typen aus [types] (HEART_RATE, STEPS, ACTIVE_CALORIES); die übrigen bleiben null.
     */
    suspend fun aggregateActivity(start: Instant, end: Instant, types: Set<HealthReadType>): HealthActivity

    /** Jüngste Schlafphase, die zwischen [from] und [to] endet, oder null. */
    suspend fun readLatestSleep(from: Instant, to: Instant): HealthSleep?
}

/** Wird nach dem Beenden eines Workouts aufgerufen; Fehler werden intern behandelt. */
fun interface FinishedWorkoutExporter {
    suspend fun export(sessionId: String)
}

/** Schritte aus Health Connect für heute und die laufende Woche, z. B. für eine Dashboard-Kachel. */
fun interface HealthStepsReader {
    /** @param firstDayOfWeek Wochenbeginn aus den Nutzereinstellungen. */
    suspend fun steps(firstDayOfWeek: DayOfWeek): HealthSteps
}

/** Schlafdauer der letzten Nacht aus Health Connect, z. B. für eine Dashboard-Kachel. */
fun interface HealthSleepReader {
    suspend fun lastNight(): HealthLastNightSleep
}

/** Liest Werte aus Health Connect für ein beendetes Workout; null, wenn nichts vorliegt oder die Anbindung aus ist. */
fun interface WorkoutHealthReader {
    suspend fun read(start: Instant, end: Instant): WorkoutHealthMetrics?
}
