package app.gympulse.core.insights

import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SetType
import java.time.LocalDate

/** Kleine Helfer für lesbare Test-Historien. */
internal object Fixtures {
    val START: LocalDate = LocalDate.of(2026, 1, 5) // Montag

    fun set(weightKg: Double?, reps: Int, rpe: Double? = null, type: SetType = SetType.WORKING, completed: Boolean = true) =
        PerformedSet(weightKg = weightKg, reps = reps, rpe = rpe, setType = type, isCompleted = completed)

    /** [count] gleiche Arbeitssätze. */
    fun sets(count: Int, weightKg: Double, reps: Int, rpe: Double? = null) = List(count) { set(weightKg, reps, rpe) }

    fun session(date: LocalDate, sets: List<PerformedSet>, bodyweightKg: Double? = null, id: String = "s-$date") =
        ExerciseSession(sessionId = id, date = date, sets = sets, bodyweightKg = bodyweightKg)

    fun bench(
        repRange: RepRange? = RepRange(8, 12),
        targetSets: Int? = null,
        incrementKg: Double? = null,
    ) = ExerciseProfile(
        exerciseId = "bench",
        equipmentType = EquipmentType.BARBELL,
        primaryMuscles = listOf(MuscleGroup.CHEST),
        secondaryMuscles = listOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
        targetRepRange = repRange,
        targetSets = targetSets,
        weightIncrementKg = incrementKg,
    )

    /**
     * Eine Session pro Woche ab [START], je ein Satz mit 1 Wiederholung: das e1RM entspricht damit
     * genau dem Gewicht, was Trend-Tests leicht nachvollziehbar macht.
     */
    fun weeklyE1rmHistory(profile: ExerciseProfile, vararg e1rms: Double, everyDays: Long = 7): ExerciseHistory =
        ExerciseHistory(
            profile = profile,
            sessions = e1rms.mapIndexed { i, kg -> session(START.plusDays(i * everyDays), listOf(set(kg, 1))) },
        )

    fun points(vararg e1rms: Double, everyDays: Long = 7): List<E1rmPoint> =
        e1rms.mapIndexed { i, kg -> E1rmPoint(START.plusDays(i * everyDays), kg) }
}
