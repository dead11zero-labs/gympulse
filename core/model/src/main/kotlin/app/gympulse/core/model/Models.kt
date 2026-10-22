package app.gympulse.core.model

import java.time.Instant
import java.time.LocalDate

data class Exercise(
    val id: String,
    val slug: String?,
    val name: String,
    val category: ExerciseCategory,
    val equipmentType: EquipmentType,
    val trackingType: TrackingType,
    val bodyweightFactor: Double?,
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val instructions: List<String>,
    val imageUrls: List<String>,
    val notes: String?,
    val isCustom: Boolean,
    val isFavorite: Boolean,
    val defaultRestSeconds: Int?,
    val equipmentId: String?,
)

data class Equipment(
    val id: String,
    val name: String,
    val location: String?,
    val notes: String?,
    val templateKey: String?,
    val settings: List<EquipmentSetting>,
)

data class EquipmentSetting(
    val id: String,
    val key: String,
    val value: String,
    val sortOrder: Int,
)

data class WorkoutPlan(
    val id: String,
    val name: String,
    val description: String?,
    val color: Long?,
    val isArchived: Boolean,
    val exercises: List<PlanExercise>,
)

data class PlanExercise(
    val id: String,
    val exerciseId: String,
    val sortOrder: Int,
    val supersetGroup: Int?,
    val targetSets: Int,
    val targetRepsMin: Int?,
    val targetRepsMax: Int?,
    val targetWeightKg: Double?,
    val restSeconds: Int?,
)

data class WorkoutSession(
    val id: String,
    val planId: String?,
    val title: String?,
    val startedAt: Instant,
    val endedAt: Instant?,
    val status: SessionStatus,
    val bodyweightKg: Double?,
    val rating: Int?,
    val notes: String?,
    val totalVolumeKg: Double?,
    val exercises: List<SessionExercise>,
)

data class SessionExercise(
    val id: String,
    val exerciseId: String,
    val sortOrder: Int,
    val supersetGroup: Int?,
    val notes: String?,
    /** Geräteeinstellungen zum Zeitpunkt des Trainings, z. B. "Sitzhöhe" → "4". */
    val equipmentSettingsSnapshot: Map<String, String>,
    val sets: List<SetEntry>,
)

data class SetEntry(
    val id: String,
    val setIndex: Int,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val rpe: Double?,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    /** Letzte Änderung des gespeicherten Satzes (`updated_at`); null, wenn er nie gespeichert wurde. */
    val updatedAt: Instant? = null,
)

data class ScheduleRule(
    val id: String,
    val planId: String,
    /** ISO-Wochentag, 1 = Montag … 7 = Sonntag. */
    val weekday: Int,
    val validFrom: LocalDate,
    val validUntil: LocalDate?,
)

data class ScheduleOverride(
    val id: String,
    val date: LocalDate,
    val planId: String?,
    val type: ScheduleOverrideType,
)

data class BodyMeasurement(
    val id: String,
    val measuredAt: Instant,
    val type: MeasurementType,
    val value: Double,
    val source: MeasurementSource,
)

/** Fortschrittsfoto; [fileName] liegt im privaten App-Speicher, das Bild verlässt das Gerät nicht. */
data class ProgressPhoto(
    val id: String,
    val takenAt: Instant,
    val fileName: String,
    val note: String?,
)

data class PersonalRecord(
    val id: String,
    val exerciseId: String,
    val type: PersonalRecordType,
    val value: Double,
    val setEntryId: String?,
    val achievedAt: Instant,
)

/** Volumen einer abgeschlossenen Session, Grundlage für die Wochen-Widgets. */
data class SessionVolume(
    val sessionId: String,
    val endedAt: Instant,
    val volumeKg: Double,
)
