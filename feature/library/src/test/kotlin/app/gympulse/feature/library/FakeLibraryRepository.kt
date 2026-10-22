package app.gympulse.feature.library

import app.gympulse.core.model.Equipment
import app.gympulse.core.model.EquipmentSetting
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.Exercise
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.PlanExercise
import app.gympulse.core.model.SetEntry
import app.gympulse.core.model.TrackingType
import app.gympulse.core.model.WorkoutPlan
import app.gympulse.feature.library.data.ExerciseDetail
import app.gympulse.feature.library.data.ExerciseDraft
import app.gympulse.feature.library.data.LibraryExercise
import app.gympulse.feature.library.data.LibraryRepository
import app.gympulse.feature.library.data.normalizeForSearch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

data class UserFieldUpdate(
    val id: String,
    val notes: String?,
    val defaultRestSeconds: Int?,
    val equipmentId: String?,
)

/** In-Memory-Ersatz für das Repository; protokolliert alle schreibenden Aufrufe. */
class FakeLibraryRepository : LibraryRepository {
    val exercises = MutableStateFlow<List<LibraryExercise>>(emptyList())
    val equipment = MutableStateFlow<List<Equipment>>(emptyList())
    val plans = MutableStateFlow<List<WorkoutPlan>>(emptyList())
    var previous: List<SetEntry> = emptyList()

    val requestedLocales = mutableListOf<String>()
    val favoriteCalls = mutableListOf<Pair<String, Boolean>>()
    val savedExercises = mutableListOf<ExerciseDraft>()
    val userFieldUpdates = mutableListOf<UserFieldUpdate>()
    val deletedExercises = mutableListOf<String>()
    val planAdds = mutableListOf<Pair<String, String>>()
    var planAddFails = false

    override fun observeExercises(locale: String): Flow<List<LibraryExercise>> {
        requestedLocales += locale
        return exercises
    }

    override fun observeExercise(id: String, locale: String): Flow<ExerciseDetail?> =
        exercises.map { list -> list.firstOrNull { it.exercise.id == id }?.exercise?.toDetail() }

    override suspend fun getExercise(id: String, locale: String): ExerciseDetail? =
        exercises.value.firstOrNull { it.exercise.id == id }?.exercise?.toDetail()

    override fun observeEquipment(): Flow<List<Equipment>> = equipment

    override fun observePlans(): Flow<List<WorkoutPlan>> = plans

    override suspend fun addExerciseToPlan(planId: String, exercise: Exercise): Boolean {
        if (planAddFails) throw IllegalStateException("db down")
        planAdds += planId to exercise.id
        return plans.value.any { it.id == planId }
    }

    override suspend fun previousSets(exerciseId: String): List<SetEntry> = previous

    override suspend fun setFavorite(id: String, favorite: Boolean) {
        favoriteCalls += id to favorite
        exercises.value = exercises.value.map {
            if (it.exercise.id == id) it.copy(exercise = it.exercise.copy(isFavorite = favorite)) else it
        }
    }

    override suspend fun saveCustomExercise(draft: ExerciseDraft, locale: String): String {
        savedExercises += draft
        return draft.id ?: "new-id"
    }

    override suspend fun updateExerciseUserFields(
        id: String,
        notes: String?,
        defaultRestSeconds: Int?,
        equipmentId: String?,
    ) {
        userFieldUpdates += UserFieldUpdate(id, notes, defaultRestSeconds, equipmentId)
    }

    override suspend fun deleteCustomExercise(id: String) {
        deletedExercises += id
    }

    private fun Exercise.toDetail() = ExerciseDetail(
        exercise = this,
        source = if (isCustom) null else "free-exercise-db",
        license = if (isCustom) null else "Unlicense",
        instructionsInEnglishOnly = false,
    )
}

fun testExercise(
    id: String,
    name: String,
    category: ExerciseCategory = ExerciseCategory.STRENGTH,
    equipmentType: EquipmentType = EquipmentType.BARBELL,
    primary: List<MuscleGroup> = emptyList(),
    secondary: List<MuscleGroup> = emptyList(),
    favorite: Boolean = false,
    custom: Boolean = false,
    equipmentId: String? = null,
    notes: String? = null,
    defaultRestSeconds: Int? = null,
    trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    bodyweightFactor: Double? = null,
    instructions: List<String> = emptyList(),
) = Exercise(
    id = id,
    slug = if (custom) null else id,
    name = name,
    category = category,
    equipmentType = equipmentType,
    trackingType = trackingType,
    bodyweightFactor = bodyweightFactor,
    primaryMuscles = primary,
    secondaryMuscles = secondary,
    instructions = instructions,
    imageUrls = emptyList(),
    notes = notes,
    isCustom = custom,
    isFavorite = favorite,
    defaultRestSeconds = defaultRestSeconds,
    equipmentId = equipmentId,
)

/** Listeneintrag mit Suchtext aus dem Namen und optionalen weiteren Sprachvarianten. */
fun testLibraryExercise(exercise: Exercise, vararg otherNames: String) = LibraryExercise(
    exercise = exercise,
    searchKey = normalizeForSearch((listOf(exercise.name) + otherNames).joinToString(" ")),
)

fun testEquipment(id: String, name: String, settings: List<Pair<String, String>> = emptyList()) = Equipment(
    id = id,
    name = name,
    location = null,
    notes = null,
    templateKey = null,
    settings = settings.mapIndexed { index, (key, value) ->
        EquipmentSetting(id = "$id-s$index", key = key, value = value, sortOrder = index)
    },
)

fun testPlan(id: String, name: String, exerciseCount: Int = 0) = WorkoutPlan(
    id = id,
    name = name,
    description = null,
    color = null,
    isArchived = false,
    exercises = List(exerciseCount) { index ->
        PlanExercise(
            id = "$id-$index",
            exerciseId = "x$index",
            sortOrder = index,
            supersetGroup = null,
            targetSets = 3,
            targetRepsMin = 8,
            targetRepsMax = 12,
            targetWeightKg = null,
            restSeconds = null,
        )
    },
)
