package app.gympulse.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class InputValidationTest {
    @Test
    fun weight_acceptsRangeBoundsAndBothSeparators() {
        assertEquals(Validated.Ok(20.0), InputValidation.weightKg("20"))
        assertEquals(Validated.Ok(400.0), InputValidation.weightKg("400"))
        assertEquals(Validated.Ok(82.5), InputValidation.weightKg("82.5"))
        assertEquals(Validated.Ok(82.5), InputValidation.weightKg(" 82,5 "))
    }

    @Test
    fun weight_rejectsOutOfRangeAndGarbage() {
        assertEquals(Validated.Invalid, InputValidation.weightKg("19.9"))
        assertEquals(Validated.Invalid, InputValidation.weightKg("400.5"))
        assertEquals(Validated.Invalid, InputValidation.weightKg(""))
        assertEquals(Validated.Invalid, InputValidation.weightKg("abc"))
        assertEquals(Validated.Invalid, InputValidation.weightKg("-70"))
        assertEquals(Validated.Invalid, InputValidation.weightKg("1e2"))
        assertEquals(Validated.Invalid, InputValidation.weightKg("NaN"))
    }

    @Test
    fun height_isOptional() {
        assertEquals(Validated.Ok(null), InputValidation.heightCm(""))
        assertEquals(Validated.Ok(null), InputValidation.heightCm("   "))
        assertEquals(Validated.Ok(180.0), InputValidation.heightCm("180"))
        assertEquals(Validated.Ok(100.0), InputValidation.heightCm("100"))
        assertEquals(Validated.Ok(250.0), InputValidation.heightCm("250"))
        assertEquals(Validated.Invalid, InputValidation.heightCm("99"))
        assertEquals(Validated.Invalid, InputValidation.heightCm("251"))
        assertEquals(Validated.Invalid, InputValidation.heightCm("tall"))
    }

    @Test
    fun name_isTrimmedOptionalAndLengthLimited() {
        assertEquals(Validated.Ok("Alex"), InputValidation.name("  Alex "))
        assertEquals(Validated.Ok(null), InputValidation.name("   "))
        assertEquals(Validated.Ok("a".repeat(50)), InputValidation.name("a".repeat(50)))
        assertEquals(Validated.Invalid, InputValidation.name("a".repeat(51)))
    }

    @Test
    fun restTimer_rangeIs10To600Seconds() {
        assertEquals(Validated.Ok(90), InputValidation.restTimerSeconds("90"))
        assertEquals(Validated.Ok(10), InputValidation.restTimerSeconds("10"))
        assertEquals(Validated.Ok(600), InputValidation.restTimerSeconds("600"))
        assertEquals(Validated.Invalid, InputValidation.restTimerSeconds("9"))
        assertEquals(Validated.Invalid, InputValidation.restTimerSeconds("601"))
        assertEquals(Validated.Invalid, InputValidation.restTimerSeconds("90.5"))
        assertEquals(Validated.Invalid, InputValidation.restTimerSeconds(""))
    }

    @Test
    fun weeklyGoal_rangeIs1To14() {
        assertEquals(Validated.Ok(1), InputValidation.weeklyGoal("1"))
        assertEquals(Validated.Ok(14), InputValidation.weeklyGoal("14"))
        assertEquals(Validated.Invalid, InputValidation.weeklyGoal("0"))
        assertEquals(Validated.Invalid, InputValidation.weeklyGoal("15"))
    }
}
