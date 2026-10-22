package app.gympulse.core.data.health

import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MeasurementSource
import app.gympulse.core.model.MeasurementType
import app.gympulse.core.model.SessionStatus
import app.gympulse.core.model.WorkoutSession
import java.time.ZoneId
import java.util.Locale

/** Reine Abbildungen zwischen GymPulse-Modellen und Health-Connect-Daten. */
object WorkoutHealthMapper {

    /**
     * Bildet eine abgeschlossene Session ab. Liefert null für aktive oder verworfene Sessions
     * sowie für Sessions ohne gültigen Zeitraum (Ende nicht nach Start).
     *
     * Der Titel lautet „GymPulse: <Plan/Titel>“. Für jede Übung mit abgeschlossenen Sätzen entsteht
     * ein Abschnitt ([HealthSegmentPlanner]) und eine Zeile in den Notizen ([HealthWorkoutNotes]).
     *
     * @param exercises Übungen je ID mit Namen in App-Sprache; unbekannte Übungen werden ignoriert.
     * @param locale Zahlenformat der Notizen.
     */
    fun toHealthWorkout(
        session: WorkoutSession,
        exercises: Map<String, Exercise>,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): HealthWorkout? {
        if (session.status != SessionStatus.COMPLETED) return null
        val end = session.endedAt ?: return null
        if (!end.isAfter(session.startedAt)) return null

        val ordered = session.exercises.sortedBy { it.sortOrder }
        val trained = ordered.filter { exercise -> exercise.sets.any { it.isCompleted } }
        val relevant = trained.ifEmpty { ordered }
        val type = exerciseType(relevant.mapNotNull { exercises[it.exerciseId]?.category })

        val details = trained.mapNotNull { sessionExercise ->
            val exercise = exercises[sessionExercise.exerciseId] ?: return@mapNotNull null
            TrainedExercise(
                name = exercise.name,
                type = HealthSegmentClassifier.classify(exercise),
                sets = sessionExercise.sets.filter { it.isCompleted }.sortedBy { it.setIndex },
            )
        }

        return HealthWorkout(
            clientRecordId = session.id,
            clientRecordVersion = end.toEpochMilli(),
            title = title(session.title),
            notes = HealthWorkoutNotes.build(session.notes, details, locale),
            startTime = session.startedAt,
            startZoneOffset = zone.rules.getOffset(session.startedAt),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            exerciseType = type,
            segments = HealthSegmentPlanner.plan(session.startedAt, end, details),
        )
    }

    /** „GymPulse: Push Day“, ohne Titel nur „GymPulse“. */
    fun title(sessionTitle: String?): String =
        sessionTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { "$TITLE_PREFIX: $it" } ?: TITLE_PREFIX

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

    private const val TITLE_PREFIX = "GymPulse"

    private val WEIGHTLIFTING_CATEGORIES = setOf(
        ExerciseCategory.POWERLIFTING,
        ExerciseCategory.OLYMPIC_WEIGHTLIFTING,
    )
}
