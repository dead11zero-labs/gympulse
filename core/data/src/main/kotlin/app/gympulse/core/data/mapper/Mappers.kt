package app.gympulse.core.data.mapper

import app.gympulse.core.database.entity.BodyMeasurementEntity
import app.gympulse.core.database.entity.PlanExerciseEntity
import app.gympulse.core.database.entity.SetEntryEntity
import app.gympulse.core.database.relation.EquipmentWithSettings
import app.gympulse.core.database.relation.ExerciseWithDetails
import app.gympulse.core.database.relation.PlanWithExercises
import app.gympulse.core.database.relation.SessionWithExercises
import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.Equipment
import app.gympulse.core.model.EquipmentSetting
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.resolveTracking
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.SessionExercise
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.core.model.WorkoutSession
import kotlinx.serialization.json.Json
import java.time.Instant

private val json = Json { ignoreUnknownKeys = true }

private fun String?.lines(): List<String> = this?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

/**
 * Wählt die Übersetzung für [locale] (z. B. "de"), sonst Englisch, sonst die erste vorhandene.
 */
fun ExerciseWithDetails.toModel(locale: String): Exercise {
    val visibleTranslations = translations.filter { it.deletedAt == null }
    val translation = visibleTranslations.firstOrNull { it.locale == locale }
        ?: visibleTranslations.firstOrNull { it.locale == "en" }
        ?: visibleTranslations.firstOrNull()
    val visibleMuscles = muscles.filter { it.deletedAt == null }
    val category = ExerciseCategory.fromKey(exercise.category)
    return Exercise(
        id = exercise.id,
        slug = exercise.slug,
        name = translation?.name ?: exercise.slug.orEmpty(),
        category = category,
        equipmentType = EquipmentType.fromKey(exercise.equipmentType),
        trackingType = category.resolveTracking(exercise.trackingType),
        bodyweightFactor = exercise.bodyweightFactor,
        primaryMuscles = visibleMuscles.filter { it.isPrimary }.mapNotNull { MuscleGroup.fromKey(it.muscle) },
        secondaryMuscles = visibleMuscles.filterNot { it.isPrimary }.mapNotNull { MuscleGroup.fromKey(it.muscle) },
        instructions = translation?.instructions.lines(),
        imageUrls = exercise.imageUrls.lines(),
        notes = exercise.notes,
        isCustom = exercise.isCustom,
        isFavorite = exercise.isFavorite,
        defaultRestSeconds = exercise.defaultRestSeconds,
        equipmentId = exercise.equipmentId,
    )
}

fun EquipmentWithSettings.toModel() = Equipment(
    id = equipment.id,
    name = equipment.name,
    location = equipment.location,
    notes = equipment.notes,
    templateKey = equipment.templateKey,
    settings = settings
        .filter { it.deletedAt == null }
        .sortedBy { it.sortOrder }
        .map { EquipmentSetting(id = it.id, key = it.key, value = it.value, sortOrder = it.sortOrder) },
)

fun PlanExerciseEntity.toModel() = PlanExercise(
    id = id,
    exerciseId = exerciseId,
    sortOrder = sortOrder,
    supersetGroup = supersetGroup,
    targetSets = targetSets,
    targetRepsMin = targetRepsMin,
    targetRepsMax = targetRepsMax,
    targetWeightKg = targetWeightKg,
    restSeconds = restSeconds,
)

fun PlanWithExercises.toModel() = WorkoutPlan(
    id = plan.id,
    name = plan.name,
    description = plan.description,
    color = plan.color,
    isArchived = plan.isArchived,
    exercises = exercises.filter { it.deletedAt == null }.sortedBy { it.sortOrder }.map { it.toModel() },
)

fun SetEntryEntity.toModel() = SetEntry(
    id = id,
    setIndex = setIndex,
    setType = setType,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    rpe = rpe,
    isCompleted = isCompleted,
    completedAt = completedAt?.let(Instant::ofEpochMilli),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

fun SessionWithExercises.toModel() = WorkoutSession(
    id = session.id,
    planId = session.planId,
    title = session.title,
    startedAt = Instant.ofEpochMilli(session.startedAt),
    endedAt = session.endedAt?.let(Instant::ofEpochMilli),
    status = session.status,
    bodyweightKg = session.bodyweightKg,
    rating = session.rating,
    notes = session.notes,
    totalVolumeKg = session.totalVolumeKg,
    exercises = exercises
        .filter { it.sessionExercise.deletedAt == null }
        .sortedBy { it.sessionExercise.sortOrder }
        .map { item ->
            SessionExercise(
                id = item.sessionExercise.id,
                exerciseId = item.sessionExercise.exerciseId,
                sortOrder = item.sessionExercise.sortOrder,
                supersetGroup = item.sessionExercise.supersetGroup,
                notes = item.sessionExercise.notes,
                equipmentSettingsSnapshot = decodeSettings(item.sessionExercise.equipmentSettingsSnapshot),
                sets = item.sets.filter { it.deletedAt == null }.sortedBy { it.setIndex }.map { it.toModel() },
            )
        },
)

fun BodyMeasurementEntity.toModel() = BodyMeasurement(
    id = id,
    measuredAt = Instant.ofEpochMilli(measuredAt),
    type = type,
    value = value,
    source = source,
)

fun encodeSettings(settings: Map<String, String>): String? =
    if (settings.isEmpty()) null else json.encodeToString(settings)

fun decodeSettings(raw: String?): Map<String, String> =
    if (raw.isNullOrBlank()) emptyMap() else runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
