package app.gympulse.wear.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import app.gympulse.wear.R
import app.gympulse.wear.theme.GymPulseWearTheme
import app.gympulse.wear.workout.HeartRateReading
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HeartRateBadgeTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun show(reading: HeartRateReading) = rule.setContent {
        GymPulseWearTheme { HeartRateBadge(reading = reading) }
    }

    @Test
    fun bpm_showsValueWithDescription() {
        show(HeartRateReading.Bpm(value = 142, sequence = 1))

        rule.onNodeWithText("142").assertExists()
        rule.onNodeWithContentDescription(string(R.string.cd_heart_rate_value, 142)).assertExists()
    }

    @Test
    fun searching_showsPlaceholder() {
        show(HeartRateReading.Searching)

        rule.onNodeWithText(string(R.string.heart_rate_placeholder)).assertExists()
    }

    @Test
    fun noPermission_showsHint() {
        show(HeartRateReading.NoPermission)

        rule.onNodeWithText(string(R.string.heart_rate_no_permission)).assertExists()
    }

    @Test
    fun unavailable_showsHint() {
        show(HeartRateReading.Unavailable)

        rule.onNodeWithText(string(R.string.heart_rate_unavailable)).assertExists()
    }

    @Test
    fun off_showsNothing() {
        show(HeartRateReading.Off)

        rule.onNodeWithText(string(R.string.heart_rate_unit)).assertDoesNotExist()
        rule.onNodeWithText(string(R.string.heart_rate_placeholder)).assertDoesNotExist()
    }
}
