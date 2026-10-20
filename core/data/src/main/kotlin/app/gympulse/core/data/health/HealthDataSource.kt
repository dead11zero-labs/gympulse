package app.gympulse.core.data.health

import java.time.Instant

/**
 * Zugriff auf Health Connect. Die Android-Implementierung liegt in `core:health`; hier steht nur
 * die Schnittstelle, damit Logik und Tests ohne Android-Laufzeit auskommen.
 */
interface HealthDataSource {
    /** Berechtigungen (Health-Connect-Strings), die die App für Export und Gewichtsimport braucht. */
    val requiredPermissions: Set<String>

    fun availability(): HealthAvailability

    /** Bereits erteilte Berechtigungen; leer, wenn Health Connect nicht verfügbar ist. */
    suspend fun grantedPermissions(): Set<String>

    /** Schreibt eine Trainingseinheit. Wirft bei fehlender Berechtigung oder Fehlern des Dienstes. */
    suspend fun insertWorkout(workout: HealthWorkout)

    /** Jüngstes Körpergewicht seit [since] oder null. */
    suspend fun readLatestWeight(since: Instant): HealthWeight?
}

/** Wird nach dem Beenden eines Workouts aufgerufen; Fehler werden intern behandelt. */
fun interface FinishedWorkoutExporter {
    suspend fun export(sessionId: String)
}
