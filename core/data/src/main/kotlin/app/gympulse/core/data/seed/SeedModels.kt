package app.gympulse.core.data.seed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Format der mitgelieferten Übungsdatenbank (assets/seed/exercises_v1.json). */
@Serializable
internal data class SeedFile(
    @SerialName("schema_version") val schemaVersion: Int,
    val exercises: List<SeedExercise>,
)

@Serializable
internal data class SeedExercise(
    val slug: String,
    val name: Map<String, String>,
    val category: String,
    @SerialName("equipment_type") val equipmentType: String,
    @SerialName("tracking_type") val trackingType: String,
    @SerialName("bodyweight_factor") val bodyweightFactor: Double? = null,
    @SerialName("primary_muscles") val primaryMuscles: List<String> = emptyList(),
    @SerialName("secondary_muscles") val secondaryMuscles: List<String> = emptyList(),
    val instructions: Map<String, List<String>> = emptyMap(),
    val images: List<String> = emptyList(),
    val source: String? = null,
    val license: String? = null,
)

private val seedJson = Json { ignoreUnknownKeys = true }

/** Parst die Seed-Datei; rein und ohne Android-Abhängigkeit, damit sie als Unit-Test laufen kann. */
internal fun parseSeed(text: String): SeedFile = seedJson.decodeFromString(SeedFile.serializer(), text)
