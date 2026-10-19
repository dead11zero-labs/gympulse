package app.gympulse.core.data.seed

import app.gympulse.core.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeedParsingTest {
    private val json = """
        {"schema_version":3,"source":{"name":"x"},"muscle_groups":[],"equipment_types":[],
         "exercises":[{"slug":"bench_press","name":{"en":"Bench Press","de":"Bankdrücken"},
         "category":"strength","equipment_type":"barbell","tracking_type":"WEIGHT_REPS",
         "bodyweight_factor":null,"primary_muscles":["chest"],"secondary_muscles":["triceps","chest"],
         "instructions":{"en":["Lie down.","Press."]},"images":["a.jpg","b.jpg"],
         "source":"free-exercise-db","license":"Unlicense","level":"beginner"}]}
    """.trimIndent()

    @Test
    fun stableIdsAreDeterministicAndDistinct() {
        assertEquals(exerciseId("bench_press"), exerciseId("bench_press"))
        assertNotEquals(exerciseId("bench_press"), exerciseId("squat"))
        assertNotEquals(translationId("bench_press", "en"), translationId("bench_press", "de"))
        assertNotEquals(muscleId("bench_press", "chest"), muscleId("bench_press", "triceps"))
        assertEquals(36, exerciseId("bench_press").length)
    }

    @Test
    fun parsesSeedAndIgnoresUnknownKeys() {
        val seed = parseSeed(json)
        assertEquals(3, seed.schemaVersion)
        val e = seed.exercises.single()
        assertEquals("Bankdrücken", e.name["de"])
        assertNull(e.bodyweightFactor)
        assertEquals(listOf("a.jpg", "b.jpg"), e.images)
    }

    @Test
    fun mapsToEntitiesAndKeepsUserFields() {
        val e = parseSeed(json).exercises.single()
        val fresh = e.toEntity(null, now = 10L)
        assertEquals(TrackingType.WEIGHT_REPS, fresh.trackingType)
        assertEquals("a.jpg\nb.jpg", fresh.imageUrls)
        val kept = e.toEntity(fresh.copy(isFavorite = true, notes = "n", defaultRestSeconds = 90, equipmentId = "eq", createdAt = 1L), now = 20L)
        assertTrue(kept.isFavorite)
        assertEquals("n", kept.notes)
        assertEquals(90, kept.defaultRestSeconds)
        assertEquals("eq", kept.equipmentId)
        assertEquals(1L, kept.createdAt)
        assertEquals(20L, kept.updatedAt)

        val tr = e.toTranslations(emptyList(), 10L).associateBy { it.locale }
        assertEquals("Lie down.\nPress.", tr.getValue("en").instructions)
        assertNull(tr.getValue("de").instructions)
        val mu = e.toMuscles(emptyList(), 10L)
        assertEquals(listOf("chest" to true, "triceps" to false), mu.map { it.muscle to it.isPrimary })
    }
}
