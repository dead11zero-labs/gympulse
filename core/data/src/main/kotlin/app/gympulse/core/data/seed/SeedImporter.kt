package app.gympulse.core.data.seed

import android.content.Context
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.ExerciseDao
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.ExerciseMuscleEntity
import app.gympulse.core.database.entity.ExerciseTranslationEntity
import app.gympulse.core.model.TrackingType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Importiert die mitgelieferte Übungsdatenbank beim ersten Start bzw. wenn die Seed-Version steigt.
 * Idempotent: Nutzeränderungen (Favorit, Notiz, Pause, Gerät) bleiben erhalten.
 */
@Singleton
class SeedImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val timeProvider: TimeProvider,
) {
    suspend fun importIfNeeded() = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val importedVersion = prefs.getInt(KEY_VERSION, 0)

        val seed = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { parseSeed(it.readText()) }
        if (seed.schemaVersion <= importedVersion) return@withContext

        val now = timeProvider.now().toEpochMilli()
        // In Blöcken schreiben, damit die einzelnen Transaktionen klein bleiben.
        seed.exercises.chunked(CHUNK_SIZE).forEach { chunk ->
            val exercises = ArrayList<ExerciseEntity>()
            val translations = ArrayList<ExerciseTranslationEntity>()
            val muscles = ArrayList<ExerciseMuscleEntity>()
            for (item in chunk) {
                val existing = exerciseDao.getById(exerciseId(item.slug))
                exercises += item.toEntity(existing?.exercise, now)
                translations += item.toTranslations(existing?.translations.orEmpty(), now)
                muscles += item.toMuscles(existing?.muscles.orEmpty(), now)
            }
            exerciseDao.upsertFull(exercises, translations, muscles)
        }
        prefs.edit().putInt(KEY_VERSION, seed.schemaVersion).apply()
    }

    private companion object {
        const val ASSET = "seed/exercises_v1.json"
        const val PREFS = "gympulse_seed"
        const val KEY_VERSION = "exercises_version"
        const val CHUNK_SIZE = 100
    }
}

/** Bestehende Zeile: Nutzerfelder, created_at und deleted_at bleiben, der Rest kommt aus dem Seed. */
internal fun SeedExercise.toEntity(existing: ExerciseEntity?, now: Long) = ExerciseEntity(
    id = exerciseId(slug),
    slug = slug,
    category = category,
    equipmentType = equipmentType,
    trackingType = TrackingType.valueOf(trackingType),
    bodyweightFactor = bodyweightFactor,
    imageUrls = images.joinToString("\n").ifEmpty { null },
    notes = existing?.notes,
    isCustom = false,
    isFavorite = existing?.isFavorite ?: false,
    defaultRestSeconds = existing?.defaultRestSeconds,
    equipmentId = existing?.equipmentId,
    source = source,
    license = license,
    createdAt = existing?.createdAt ?: now,
    updatedAt = now,
    deletedAt = existing?.deletedAt,
)

/** Namen in allen vorhandenen Sprachen; Anleitung nur auf Englisch. */
internal fun SeedExercise.toTranslations(
    existing: List<ExerciseTranslationEntity>,
    now: Long,
): List<ExerciseTranslationEntity> = name.map { (locale, text) ->
    ExerciseTranslationEntity(
        id = translationId(slug, locale),
        exerciseId = exerciseId(slug),
        locale = locale,
        name = text,
        instructions = if (locale == "en") instructions["en"]?.joinToString("\n") else null,
        createdAt = existing.firstOrNull { it.locale == locale }?.createdAt ?: now,
        updatedAt = now,
    )
}

internal fun SeedExercise.toMuscles(
    existing: List<ExerciseMuscleEntity>,
    now: Long,
): List<ExerciseMuscleEntity> {
    val rows = primaryMuscles.map { it to true } + secondaryMuscles.filterNot { it in primaryMuscles }.map { it to false }
    return rows.distinctBy { it.first }.map { (muscle, primary) ->
        ExerciseMuscleEntity(
            id = muscleId(slug, muscle),
            exerciseId = exerciseId(slug),
            muscle = muscle,
            isPrimary = primary,
            createdAt = existing.firstOrNull { it.muscle == muscle }?.createdAt ?: now,
            updatedAt = now,
        )
    }
}
