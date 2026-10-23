package app.gympulse.core.data.seed

import app.gympulse.core.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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

    private fun realSeed(): SeedFile {
        val file = File("../database/src/main/assets/seed/exercises_v1.json")
        return parseSeed(file.readText(Charsets.UTF_8))
    }

    @Test
    fun realSeedHasUniqueSlugsAndIds() {
        val exercises = realSeed().exercises
        assertEquals(exercises.size, exercises.map { it.slug }.toSet().size)
        assertEquals(exercises.size, exercises.map { exerciseId(it.slug) }.toSet().size)
    }

    @Test
    fun realSeedCardioExercisesTrackOnlyDuration() {
        val cardio = realSeed().exercises.filter { it.category == "cardio" }
        assertTrue(cardio.isNotEmpty())
        cardio.forEach { assertEquals(it.slug, "DURATION", it.trackingType) }
    }

    @Test
    fun realSeedContainsMachinesFromJoerg() {
        val seed = realSeed()
        assertTrue(seed.schemaVersion >= 2)
        val machines = seed.exercises.filter { it.source == "gympulse" }
        assertEquals(18, machines.size)
        machines.forEach {
            assertEquals("CC0-1.0", it.license)
            assertTrue(it.name.containsKey("de") && it.name.containsKey("en"))
            assertTrue(it.primaryMuscles.isNotEmpty())
        }
        val pendulum = machines.single { it.slug == "pendulum_squat" }
        assertEquals("Pendel-Kniebeuge", pendulum.name["de"])
        assertEquals("Pendulum Squat", pendulum.name["en"])
        assertEquals("machine", pendulum.equipmentType)
        assertEquals("WEIGHT_REPS", pendulum.trackingType)
        assertEquals(listOf("quadriceps", "glutes"), pendulum.primaryMuscles)
        val chair = machines.single { it.slug == "captains_chair" }
        assertEquals("bodyweight", chair.equipmentType)
        assertEquals("REPS", chair.trackingType)
        assertEquals(0.35, chair.bodyweightFactor!!, 0.0)
    }

    @Test
    fun machineDuplicatesAreNotAddedTwice() {
        val slugs = realSeed().exercises.map { it.slug }.toSet()
        assertTrue("leg_press" in slugs && "45_degree_leg_press" in slugs)
        assertTrue("wide_grip_lat_pulldown" in slugs && "lat_pulldown" !in slugs)
    }
}
