package app.gympulse.core.data.repository

import android.content.Context
import app.gympulse.core.model.EquipmentTemplate
import app.gympulse.core.model.EquipmentTemplateSetting
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.MuscleGroup
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Liest die mitgelieferten Gerätevorlagen einmalig aus den Assets und hält sie im Speicher. */
@Singleton
class EquipmentTemplateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private var cache: TemplateFile? = null

    /** Vorlagen mit Namen in [locale] (z. B. "de"), sonst Englisch. */
    suspend fun templates(locale: String): List<EquipmentTemplate> {
        val file = load()
        return file.templates.map { it.toModel(locale) }
    }

    private suspend fun load(): TemplateFile = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            val text = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseTemplates(text)
        }.also { cache = it }
    }

    private companion object {
        const val ASSET = "seed/equipment_templates_v1.json"
    }
}

@Serializable
internal data class TemplateFile(val templates: List<TemplateDto>)

@Serializable
internal data class TemplateDto(
    val key: String,
    @SerialName("equipment_type") val equipmentType: String,
    val name: Map<String, String>,
    @SerialName("muscle_groups") val muscleGroups: List<String> = emptyList(),
    @SerialName("setting_keys") val settingKeys: List<SettingDto> = emptyList(),
)

@Serializable
internal data class SettingDto(
    val key: String,
    val name: Map<String, String>,
    @SerialName("value_hint") val valueHint: Map<String, String> = emptyMap(),
)

private val templateJson = Json { ignoreUnknownKeys = true }

internal fun parseTemplates(text: String): TemplateFile =
    templateJson.decodeFromString(TemplateFile.serializer(), text)

private fun Map<String, String>.localized(locale: String, fallback: String): String =
    this[locale] ?: this["en"] ?: fallback

internal fun TemplateDto.toModel(locale: String) = EquipmentTemplate(
    key = key,
    equipmentType = EquipmentType.fromKey(equipmentType),
    name = name.localized(locale, key),
    muscleGroups = muscleGroups.mapNotNull { MuscleGroup.fromKey(it) },
    settingKeys = settingKeys.map {
        EquipmentTemplateSetting(
            key = it.key,
            name = it.name.localized(locale, it.key),
            valueHint = it.valueHint.localized(locale, ""),
        )
    },
)
