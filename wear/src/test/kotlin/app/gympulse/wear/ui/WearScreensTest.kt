package app.gympulse.wear.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.gympulse.core.wearprotocol.AckResult
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.core.wearprotocol.ExerciseSnapshot
import app.gympulse.core.wearprotocol.PlanSummary
import app.gympulse.core.wearprotocol.PreviousSet
import app.gympulse.core.wearprotocol.SessionSnapshot
import app.gympulse.core.wearprotocol.SetSnapshot
import app.gympulse.wear.R
import app.gympulse.wear.data.WearConnection
import app.gympulse.wear.theme.GymPulseWearTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WearScreensTest {
    @get:Rule
    val rule = createComposeRule()

    private fun string(@StringRes id: Int, vararg args: Any): String =
        ApplicationProvider.getApplicationContext<Context>().getString(id, *args)

    private fun workoutState(
        connection: WearConnection = WearConnection.CONNECTED,
        notices: List<CommandAck> = emptyList(),
        done: Boolean = false,
    ) = WorkoutUiState(
        session = SessionSnapshot(
            sessionId = "s1",
            startedAt = 0,
            exercises = listOf(
                ExerciseSnapshot(
                    sessionExerciseId = "e1",
                    exerciseId = "x1",
                    name = "Bench Press",
                    sets = listOf(
                        SetSnapshot(
                            setId = "set1",
                            number = 1,
                            weightKg = 82.5,
                            reps = 8,
                            isCompleted = done,
                            updatedAt = 0,
                            previous = PreviousSet(weightKg = 80.0, reps = 8),
                        ),
                        SetSnapshot(setId = "set2", number = 2, isCompleted = done, updatedAt = 0),
                    ),
                ),
            ),
        ),
        connection = connection,
        notices = notices,
    )

    private fun setWorkoutContent(
        state: WorkoutUiState,
        restRemaining: Int? = null,
        onComplete: () -> Unit = {},
        onAdjustRest: (Int) -> Unit = {},
        onSkipRest: () -> Unit = {},
        onSelectField: (EditField) -> Unit = {},
        onDismiss: () -> Unit = {},
        onFinish: () -> Unit = {},
    ) = rule.setContent {
        GymPulseWearTheme {
            WorkoutScreen(
                state = state,
                restRemainingSeconds = restRemaining,
                onSelectField = onSelectField,
                onRotate = {},
                onCompleteSet = onComplete,
                onPreviousExercise = {},
                onNextExercise = {},
                onOpenOverview = {},
                onFinish = onFinish,
                onAdjustRest = onAdjustRest,
                onSkipRest = onSkipRest,
                onDismissNotices = onDismiss,
            )
        }
    }

    @Test
    fun workoutShowsExerciseSetValuesAndPrevious() {
        setWorkoutContent(workoutState())
        rule.onNodeWithText("Bench Press").assertExists()
        rule.onNodeWithText(string(R.string.workout_set_progress, 1, 2)).assertExists()
        rule.onNodeWithText("82.5").assertExists()
        rule.onNodeWithText("8").assertExists()
        rule.onNodeWithText(string(R.string.workout_previous_weight_reps, "80", 8)).assertExists()
    }

    @Test
    fun tapOnDoneCompletesTheSet() {
        var completed = 0
        setWorkoutContent(workoutState(), onComplete = { completed++ })
        rule.onNodeWithText(string(R.string.workout_complete)).performClick()
        assertEquals(1, completed)
    }

    @Test
    fun tapOnValueSelectsField() {
        var field: EditField? = null
        setWorkoutContent(workoutState(), onSelectField = { field = it })
        rule.onNodeWithContentDescription(string(R.string.cd_reps_value, 8)).performClick()
        assertEquals(EditField.REPS, field)
    }

    @Test
    fun restCountdownIsShownAndControllable() {
        var delta = 0
        var skipped = 0
        setWorkoutContent(workoutState(), restRemaining = 83, onAdjustRest = { delta += it }, onSkipRest = { skipped++ })
        rule.onNodeWithText("1:23").assertExists()
        rule.onNodeWithContentDescription(string(R.string.cd_rest_add)).performClick()
        rule.onNodeWithContentDescription(string(R.string.cd_rest_subtract)).performClick()
        rule.onNodeWithContentDescription(string(R.string.cd_rest_skip, "1:23")).performClick()
        assertEquals(0, delta)
        assertEquals(1, skipped)
    }

    @Test
    fun offlineHintAndNoticesAreShown() {
        var dismissed = 0
        setWorkoutContent(
            workoutState(
                connection = WearConnection.DISCONNECTED,
                notices = listOf(CommandAck("c1", AckResult.SESSION_ENDED)),
            ),
            onDismiss = { dismissed++ },
        )
        // Hinweise des Telefons haben Vorrang vor dem Offline-Hinweis.
        rule.onNodeWithText(string(R.string.notice_session_ended)).assertExists().performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun offlineHintIsShownWhenDisconnected() {
        setWorkoutContent(workoutState(connection = WearConnection.DISCONNECTED))
        rule.onNodeWithText(string(R.string.status_offline_syncing)).assertExists()
    }

    @Test
    fun finishButtonReplacesDoneWhenAllSetsAreDone() {
        var finished = 0
        setWorkoutContent(workoutState(done = true), onFinish = { finished++ })
        rule.onNodeWithText(string(R.string.workout_finish)).performClick()
        assertEquals(1, finished)
    }

    @Test
    fun plansAreListedAndStartTheWorkout() {
        val started = mutableListOf<String?>()
        rule.setContent {
            GymPulseWearTheme {
                PlansScreen(
                    state = PlansUiState(plans = listOf(PlanSummary("p1", "Push Day", 5))),
                    onStart = { started += it },
                    onDismissNotices = {},
                )
            }
        }
        rule.onNodeWithText("Push Day").performClick()
        rule.onNodeWithText(string(R.string.plans_free_workout)).performClick()
        assertEquals(listOf<String?>("p1", null), started)
    }

    @Test
    fun confirmScreenOffersConfirmAndCancel() {
        var confirmed = 0
        var cancelled = 0
        rule.setContent {
            GymPulseWearTheme {
                ConfirmEndScreen(EndKind.DISCARD, onConfirm = { confirmed++ }, onCancel = { cancelled++ })
            }
        }
        rule.onNodeWithText(string(R.string.confirm_discard_title)).assertExists()
        rule.onNodeWithText(string(R.string.confirm_cancel)).performClick()
        rule.onNodeWithText(string(R.string.confirm_discard_action)).performClick()
        assertEquals(1, confirmed)
        assertEquals(1, cancelled)
    }
}
