package app.gympulse.feature.workout

import app.gympulse.core.model.TrackingType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class WorkoutFormatTest {
    @Test
    fun parseDecimal_acceptsCommaAndDot() {
        assertEquals(62.5, WorkoutFormat.parseDecimal("62,5")!!, 0.0001)
        assertEquals(62.5, WorkoutFormat.parseDecimal("62.5")!!, 0.0001)
        assertNull(WorkoutFormat.parseDecimal(""))
        assertNull(WorkoutFormat.parseDecimal(","))
    }

    @Test
    fun sanitizeDecimal_rejectsInvalidInput() {
        assertEquals("62,5", WorkoutFormat.sanitizeDecimal("62,5"))
        assertEquals("62,", WorkoutFormat.sanitizeDecimal("62,"))
        assertNull(WorkoutFormat.sanitizeDecimal("62,5,"))
        assertNull(WorkoutFormat.sanitizeDecimal("6a"))
        assertNull(WorkoutFormat.sanitizeDecimal("1,255"))
        assertNull(WorkoutFormat.sanitizeDecimal("12345"))
    }

    @Test
    fun sanitizeInteger_onlyDigits() {
        assertEquals("12", WorkoutFormat.sanitizeInteger("12"))
        assertNull(WorkoutFormat.sanitizeInteger("1.5"))
        assertNull(WorkoutFormat.sanitizeInteger("-1"))
    }

    @Test
    fun formatDecimalInput_usesLocaleSeparator() {
        assertEquals("62,5", WorkoutFormat.formatDecimalInput(62.5, Locale.GERMAN))
        assertEquals("62.5", WorkoutFormat.formatDecimalInput(62.5, Locale.ENGLISH))
        assertEquals("100", WorkoutFormat.formatDecimalInput(100.0, Locale.GERMAN))
        assertEquals("", WorkoutFormat.formatDecimalInput(null, Locale.GERMAN))
    }

    @Test
    fun formatKg_groupsThousands() {
        assertEquals("1.234,5", WorkoutFormat.formatKg(1234.5, Locale.GERMAN))
        assertEquals("1,234.5", WorkoutFormat.formatKg(1234.5, Locale.ENGLISH))
    }

    @Test
    fun formatElapsed_minutesAndHours() {
        assertEquals("0:59", WorkoutFormat.formatElapsed(59))
        assertEquals("32:14", WorkoutFormat.formatElapsed(32 * 60 + 14))
        assertEquals("1:02:03", WorkoutFormat.formatElapsed(3723))
        assertEquals("0:00", WorkoutFormat.formatElapsed(-5))
    }

    @Test
    fun setFields_followTrackingType() {
        assertEquals(listOf(SetField.WEIGHT, SetField.REPS), setFieldsFor(TrackingType.WEIGHT_REPS, false))
        assertEquals(listOf(SetField.EXTRA_WEIGHT, SetField.REPS), setFieldsFor(TrackingType.REPS, true))
        assertEquals(listOf(SetField.REPS), setFieldsFor(TrackingType.REPS, false))
        assertEquals(listOf(SetField.DURATION), setFieldsFor(TrackingType.DURATION, true))
        assertEquals(listOf(SetField.DISTANCE, SetField.DURATION), setFieldsFor(TrackingType.DISTANCE, false))
    }

    @Test
    fun filterExercises_matchesAllWords() {
        val options = listOf(
            ExerciseOption("1", "Bench Press", false),
            ExerciseOption("2", "Incline Bench Press", true),
            ExerciseOption("3", "Squat", false),
        )

        assertEquals(listOf("1", "2"), filterExercises(options, "bench").map { it.id })
        assertEquals(listOf("2"), filterExercises(options, "press incl").map { it.id })
        assertEquals(3, filterExercises(options, "  ").size)
    }

    @Test
    fun adjustDecimal_stepsAndClamps() {
        assertEquals(52.5, WorkoutFormat.adjustDecimal(50.0, 2.5), 0.0001)
        assertEquals(2.5, WorkoutFormat.adjustDecimal(null, 2.5), 0.0001)
        assertEquals(0.0, WorkoutFormat.adjustDecimal(1.0, -2.5), 0.0001)
        assertEquals(51.25, WorkoutFormat.adjustDecimal(50.0, 1.25), 0.0001)
        assertEquals(9_999.0, WorkoutFormat.adjustDecimal(9_998.0, 5.0), 0.0001)
    }

    @Test
    fun adjustInteger_stepsAndClamps() {
        assertEquals(13, WorkoutFormat.adjustInteger(12, 1))
        assertEquals(1, WorkoutFormat.adjustInteger(null, 1))
        assertEquals(0, WorkoutFormat.adjustInteger(0, -1))
        assertEquals(9_999, WorkoutFormat.adjustInteger(9_999, 1))
    }
}
