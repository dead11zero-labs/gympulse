package app.gympulse.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenseSourcesTest {
    @Test
    fun exerciseData_listsFreeExerciseDbUnderUnlicense() {
        val source = LicenseSources.exerciseData.single { it.name == "free-exercise-db" }
        assertEquals("Unlicense", source.license)
        assertEquals("https://github.com/yuhonas/free-exercise-db", source.url)
        assertNotNull(source.descriptionRes)
    }

    @Test
    fun exerciseData_listsGymPulseContentUnderCc0() {
        val content = LicenseSources.exerciseData.single { it.name == "GymPulse" }
        assertEquals("CC0-1.0", content.license)
        assertNull(content.url)
    }

    @Test
    fun libraries_areAllApache2AndIncludeMainDependencies() {
        val names = LicenseSources.libraries.map { it.name }
        assertTrue(LicenseSources.libraries.all { it.license == "Apache-2.0" })
        listOf("Compose", "Room", "Hilt", "Coil", "Kotlin", "OkHttp", "WorkManager", "LiteRT-LM", "Gemma").forEach { expected ->
            assertTrue("missing $expected in $names", names.any { it.contains(expected) })
        }
    }

    @Test
    fun allEntries_haveNonBlankFieldsAndHttpsLinks() {
        val all = LicenseSources.exerciseData + LicenseSources.libraries
        all.forEach { entry ->
            assertTrue(entry.name.isNotBlank())
            assertTrue(entry.license.isNotBlank())
            entry.url?.let { assertTrue("$it is not https", it.startsWith("https://")) }
        }
    }

    @Test
    fun names_areUniqueWithinEachSection() {
        val exercise = LicenseSources.exerciseData.map { it.name }
        val libraries = LicenseSources.libraries.map { it.name }
        assertEquals(exercise.size, exercise.toSet().size)
        assertEquals(libraries.size, libraries.toSet().size)
    }
}
