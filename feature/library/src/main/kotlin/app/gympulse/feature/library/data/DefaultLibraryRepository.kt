package app.gympulse.feature.library.data

import app.gympulse.core.data.mapper.toModel
import app.gympulse.core.data.repository.EquipmentTemplateRepository
import app.gympulse.core.data.util.TimeProvider
import app.gympulse.core.database.dao.EquipmentDao
import app.gympulse.core.database.dao.ExerciseDao
import app.gympulse.core.database.dao.SessionDao
import app.gympulse.core.database.entity.EquipmentEntity
import app.gympulse.core.database.entity.EquipmentSettingEntity
import app.gympulse.core.database.entity.ExerciseEntity
import app.gympulse.core.database.entity.ExerciseMuscleEntity
import app.gympulse.core.database.entity.ExerciseTranslationEntity
import app.gympulse.core.database.relation.ExerciseWithDetails
import app.gympulse.core.model.Equipment
import app.gympulse.core.model.EquipmentTemplate
import app.gympulse.core.model.Ids
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.SetEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Bibliothek auf Basis der Room-DAOs. Die Filterung der Übungsliste läuft im Speicher
 * (siehe [filterExercises]); die Zuordnung der ~900 Übungen passiert abseits des Main-Threads.
 */
class DefaultLibraryRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val equipmentDao: EquipmentDao,
    private val sessionDao: SessionDao,
    private val templateRepository: EquipmentTemplateRepository,
    private val time: TimeProvider,
) : LibraryRepository {

    override fun observeExercises(locale: String): Flow<List<LibraryExercise>> =
        exerciseDao.observeAll()
            .map { rows ->
                rows.map { LibraryExercise(it.toModel(locale), it.searchKey()) }
                    .sortedWith(compareBy<LibraryExercise, String>(String.CASE_INSENSITIVE_ORDER) { it.exercise.name })
            }
            .flowOn(Dispatchers.Default)

    override fun observeExercise(id: String, locale: String): Flow<ExerciseDetail?> =
        exerciseDao.observeById(id).map { it?.toDetail(locale) }.flowOn(Dispatchers.Default)

    override suspend fun getExercise(id: String, locale: String): ExerciseDetail? =
        exerciseDao.getById(id)?.takeIf { it.exercise.deletedAt == null }?.toDetail(locale)

    override fun observeEquipment(): Flow<List<Equipment>> =
        equipmentDao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeEquipmentById(id: String): Flow<Equipment?> =
        equipmentDao.observeById(id).map { it?.toModel() }

    override suspend fun templates(locale: String): List<EquipmentTemplate> =
        templateRepository.templates(locale)

    override suspend fun previousSets(exerciseId: String): List<SetEntry> =
        sessionDao.getPreviousSets(exerciseId).map { it.toModel() }

    override suspend fun setFavorite(id: String, favorite: Boolean) =
        exerciseDao.setFavorite(id, favorite, time.now().toEpochMilli())

    override suspend fun saveCustomExercise(draft: ExerciseDraft, locale: String): String {
        val now = time.now().toEpochMilli()
        val existing = draft.id?.let { exerciseDao.getById(it) }
        require(existing == null || existing.exercise.isCustom) { "Seed exercises cannot be overwritten" }

        val id = existing?.exercise?.id ?: Ids.newId(now)
        val entity = ExerciseEntity(
            id = id,
            slug = null,
            category = draft.category.key,
            equipmentType = draft.equipmentType.key,
            trackingType = draft.trackingType,
            bodyweightFactor = draft.bodyweightFactor,
            imageUrls = existing?.exercise?.imageUrls,
            notes = draft.notes,
            isCustom = true,
            isFavorite = existing?.exercise?.isFavorite ?: false,
            defaultRestSeconds = draft.defaultRestSeconds,
            equipmentId = draft.equipmentId,
            source = existing?.exercise?.source,
            license = existing?.exercise?.license,
            createdAt = existing?.exercise?.createdAt ?: now,
            updatedAt = now,
            deletedAt = null,
        )
        val translation = buildTranslation(id, existing?.translations.orEmpty(), draft, locale, now)
        val muscles = buildMuscles(id, existing?.muscles.orEmpty(), draft.primaryMuscles, draft.secondaryMuscles, now)
        exerciseDao.upsertFull(listOf(entity), listOf(translation), muscles)
        return id
    }

    override suspend fun updateExerciseUserFields(
        id: String,
        notes: String?,
        defaultRestSeconds: Int?,
        equipmentId: String?,
    ) {
        val existing = exerciseDao.getById(id)?.exercise ?: return
        exerciseDao.upsertExercises(
            listOf(
                existing.copy(
                    notes = notes,
                    defaultRestSeconds = defaultRestSeconds,
                    equipmentId = equipmentId,
                    updatedAt = time.now().toEpochMilli(),
                ),
            ),
        )
    }

    override suspend fun deleteCustomExercise(id: String) {
        val existing = exerciseDao.getById(id)?.exercise ?: return
        if (!existing.isCustom) return
        exerciseDao.softDelete(id, time.now().toEpochMilli())
    }

    override suspend fun saveEquipment(draft: EquipmentDraft): String {
        val now = time.now().toEpochMilli()
        val existing = draft.id?.let { equipmentDao.observeById(it).first() }
        val id = existing?.equipment?.id ?: Ids.newId(now)

        equipmentDao.upsert(
            EquipmentEntity(
                id = id,
                name = draft.name.trim(),
                location = draft.location?.trim()?.ifEmpty { null },
                notes = draft.notes?.trim()?.ifEmpty { null },
                templateKey = draft.templateKey ?: existing?.equipment?.templateKey,
                createdAt = existing?.equipment?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            ),
        )

        val oldSettings = existing?.settings.orEmpty().filter { it.deletedAt == null }
        val kept = draft.settings.filter { it.key.isNotBlank() }
        val rows = kept.mapIndexed { index, setting ->
            val old = oldSettings.firstOrNull { it.id == setting.id }
            EquipmentSettingEntity(
                id = old?.id ?: Ids.newId(now),
                equipmentId = id,
                key = setting.key.trim(),
                value = setting.value.trim(),
                sortOrder = index,
                createdAt = old?.createdAt ?: now,
                updatedAt = now,
                deletedAt = null,
            )
        }
        if (rows.isNotEmpty()) equipmentDao.upsertSettings(rows)
        val keptIds = rows.map { it.id }.toSet()
        oldSettings.filter { it.id !in keptIds }.forEach { equipmentDao.softDeleteSetting(it.id, now) }
        return id
    }

    override suspend fun deleteEquipment(id: String) {
        val now = time.now().toEpochMilli()
        val linked = exerciseDao.observeAll().first().filter { it.exercise.equipmentId == id }
        if (linked.isNotEmpty()) {
            exerciseDao.upsertExercises(linked.map { it.exercise.copy(equipmentId = null, updatedAt = now) })
        }
        equipmentDao.softDelete(id, now)
    }
}

private fun ExerciseWithDetails.searchKey(): String =
    normalizeForSearch(translations.filter { it.deletedAt == null }.joinToString(" ") { it.name })

private fun String?.splitSteps(): List<String> =
    this?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

/** Die Seed-Daten enthalten Anleitungen nur auf Englisch; für andere Sprachen greift dieser Fallback. */
internal fun ExerciseWithDetails.toDetail(locale: String): ExerciseDetail {
    val base = toModel(locale)
    val fallback = if (base.instructions.isEmpty()) {
        translations.firstOrNull { it.locale == "en" && it.deletedAt == null }?.instructions.splitSteps()
    } else {
        emptyList()
    }
    return ExerciseDetail(
        exercise = if (fallback.isEmpty()) base else base.copy(instructions = fallback),
        source = exercise.source,
        license = exercise.license,
        instructionsInEnglishOnly = fallback.isNotEmpty() && locale != "en",
    )
}

private fun buildTranslation(
    exerciseId: String,
    existing: List<ExerciseTranslationEntity>,
    draft: ExerciseDraft,
    locale: String,
    now: Long,
): ExerciseTranslationEntity {
    val visible = existing.filter { it.deletedAt == null }
    val old = visible.firstOrNull { it.locale == locale } ?: visible.firstOrNull()
    val instructions = draft.instructions.map { it.trim() }.filter { it.isNotEmpty() }
        .joinToString("\n").ifEmpty { null }
    return old?.copy(name = draft.name.trim(), instructions = instructions, updatedAt = now)
        ?: ExerciseTranslationEntity(
            id = Ids.newId(now),
            exerciseId = exerciseId,
            locale = locale,
            name = draft.name.trim(),
            instructions = instructions,
            createdAt = now,
            updatedAt = now,
        )
}

/**
 * Gleicht die Muskelzeilen mit der Auswahl ab. Zeilen bleiben erhalten (eindeutiger Index auf
 * Übung und Muskel): entfernte werden per Soft Delete markiert, wieder gewählte reaktiviert.
 */
internal fun buildMuscles(
    exerciseId: String,
    existing: List<ExerciseMuscleEntity>,
    primary: Set<MuscleGroup>,
    secondary: Set<MuscleGroup>,
    now: Long,
): List<ExerciseMuscleEntity> {
    val wanted = LinkedHashMap<String, Boolean>()
    secondary.forEach { wanted[it.key] = false }
    primary.forEach { wanted[it.key] = true }

    val result = ArrayList<ExerciseMuscleEntity>()
    for ((muscle, isPrimary) in wanted) {
        val old = existing.firstOrNull { it.muscle == muscle }
        result += old?.copy(isPrimary = isPrimary, updatedAt = now, deletedAt = null)
            ?: ExerciseMuscleEntity(
                id = Ids.newId(now),
                exerciseId = exerciseId,
                muscle = muscle,
                isPrimary = isPrimary,
                createdAt = now,
                updatedAt = now,
            )
    }
    existing.filter { it.deletedAt == null && it.muscle !in wanted }
        .forEach { result += it.copy(deletedAt = now, updatedAt = now) }
    return result
}
