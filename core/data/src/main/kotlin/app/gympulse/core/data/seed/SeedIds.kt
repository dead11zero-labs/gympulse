package app.gympulse.core.data.seed

import java.util.UUID

/**
 * Stabile, namensbasierte UUIDs für Seed-Daten: gleiche Eingabe ergibt auf jedem Gerät und nach
 * jeder Neuinstallation dieselbe ID (wichtig für den späteren Sync).
 */
internal fun stableId(name: String): String = UUID.nameUUIDFromBytes(name.toByteArray()).toString()

internal fun exerciseId(slug: String) = stableId("gympulse:exercise:$slug")

internal fun translationId(slug: String, locale: String) = stableId("gympulse:exercise_translation:$slug:$locale")

internal fun muscleId(slug: String, muscle: String) = stableId("gympulse:exercise_muscle:$slug:$muscle")
