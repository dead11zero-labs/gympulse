package app.gympulse.core.data.health

import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.WorkoutSession
import java.time.ZoneId

/** Reine Abbildungen zwischen GymPulse-Modellen und Health-Connect-Daten. */
object WorkoutHealthMapper {

    /**
     * Bildet eine abgeschlossene Session ab. Liefert null für aktive oder verworfene Sessions
     * sowie für Sessions ohne gültigen Zeitraum (Ende nicht nach Start).
     *
     * @param categories Kategorie je Übungs-ID; unbekannte Übungen werden ignoriert.
     */
    fun toHealthWorkout(
        session: WorkoutSession,
        categories: Map<String, ExerciseCategory>,
        zone: ZoneId,
    ): HealthWorkout? {
        if (session.status != SessionStatus.COMPLETED) return null
        val end = session.endedAt ?: return null
        if (!end.isAfter(session.startedAt)) return null

        val trained = session.exercises.filter { exercise -> exercise.sets.any { it.isCompleted } }
        val relevant = trained.ifEmpty { session.exercises }
        val type = exerciseType(relevant.mapNotNull { categories[it.exerciseId] })

        return HealthWorkout(
            clientRecordId = session.id,
            clientRecordVersion = end.toEpochMilli(),
            title = session.title?.trim()?.takeIf { it.isNotEmpty() },
            notes = session.notes?.trim()?.takeIf { it.isNotEmpty() },
            startTime = session.startedAt,
            startZoneOffset = zone.rules.getOffset(session.startedAt),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            exerciseType = type,
        )
    }

    /**
     * Trainingsart aus den Übungskategorien: nur Dehnen ergibt STRETCHING; sonst entscheiden die
     * übrigen Übungen. Nur Powerlifting/Olympisches Gewichtheben ergibt WEIGHTLIFTING, nur
     * Plyometrie CALISTHENICS, nur Cardio OTHER_WORKOUT, alles andere STRENGTH_TRAINING.
     */
    fun exerciseType(categories: Collection<ExerciseCategory>): HealthExerciseType {
        if (categories.isEmpty()) return HealthExerciseType.STRENGTH_TRAINING
        val withoutStretching = categories.filter { it != ExerciseCategory.STRETCHING }
        if (withoutStretching.isEmpty()) return HealthExerciseType.STRETCHING
        val distinct = withoutStretching.toSet()
        return when {
            distinct.all { it in WEIGHTLIFTING_CATEGORIES } -> HealthExerciseType.WEIGHTLIFTING
            distinct == setOf(ExerciseCategory.PLYOMETRICS) -> HealthExerciseType.CALISTHENICS
            distinct == setOf(ExerciseCategory.CARDIO) -> HealthExerciseType.OTHER_WORKOUT
            else -> HealthExerciseType.STRENGTH_TRAINING
        }
    }

    /**
     * true, wenn [weight] noch nicht übernommen wurde, also kein Health-Connect-Gewicht mit
     * demselben Messzeitpunkt existiert.
     */
    fun isNewWeight(weight: HealthWeight, existing: List<BodyMeasurement>): Boolean = existing.none {
        it.type == MeasurementType.WEIGHT &&
            it.source == MeasurementSource.HEALTH_CONNECT &&
            it.measuredAt.toEpochMilli() == weight.measuredAt.toEpochMilli()
    }

    private val WEIGHTLIFTING_CATEGORIES = setOf(
        ExerciseCategory.POWERLIFTING,
        ExerciseCategory.OLYMPIC_WEIGHTLIFTING,
    )
}
