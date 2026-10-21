package app.gympulse.core.designsystem.component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.designsystem.R
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.designsystem.theme.metricUnit
import app.gympulse.core.designsystem.theme.stepperValue
import kotlinx.coroutines.delay

/**
 * Taktiler Zahlen-Stepper "− Wert +" für Gewicht und Wiederholungen (Kinetic Material,
 * "Tactile Number Steppers"). Beide Knöpfe sind 48 x 48 dp große Pillen in
 * `surfaceContainerHigh` mit `onSurface`-Symbol, der Wert steht dazwischen in
 * [stepperValue] (headline-md, Tabellenziffern), optional mit Einheit und Label darüber.
 *
 * Der Stepper hält keinen Zustand: Der Aufrufer formatiert den Wert (immer kg) und
 * entscheidet über die Schrittweite.
 *
 * Beispiel:
 * ```
 * NumberStepper(
 *     value = formatKg(state.weight),
 *     unit = stringResource(R.string.unit_kg),
 *     label = stringResource(R.string.weight_label),
 *     onDecrement = { viewModel.changeWeight(-2.5) },
 *     onIncrement = { viewModel.changeWeight(+2.5) },
 *     decrementEnabled = state.weight > 0.0,
 * )
 * ```
 *
 * @param value fertig formatierter Wert, z. B. "52,5".
 * @param onDecrement Klick auf "−".
 * @param onIncrement Klick auf "+".
 * @param unit optionale Einheit neben dem Wert, z. B. "kg".
 * @param label optionales Label über dem Stepper, wird als [LabelCaps] dargestellt.
 * @param repeatOnLongPress Halten eines Knopfes wiederholt die Aktion und wird dabei schneller
 *   (siehe [nextStepperRepeatDelayMillis]); ein einfacher Tipp löst genau einmal aus.
 * @param decrementEnabled ob "−" aktiv ist (z. B. nicht unter 0).
 * @param incrementEnabled ob "+" aktiv ist.
 * @param decrementDescription TalkBack-Text für "−"; Standard "Verringern"/"Decrease" plus Label.
 * @param incrementDescription TalkBack-Text für "+"; Standard "Erhöhen"/"Increase" plus Label.
 */
@Composable
fun NumberStepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    label: String? = null,
    decrementEnabled: Boolean = true,
    incrementEnabled: Boolean = true,
    repeatOnLongPress: Boolean = true,
    decrementDescription: String = stepperDescription(R.string.designsystem_stepper_decrease, label),
    incrementDescription: String = stepperDescription(R.string.designsystem_stepper_increase, label),
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (label != null) {
            LabelCaps(text = label)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            StepperButton(
                onClick = onDecrement,
                enabled = decrementEnabled,
                repeatOnLongPress = repeatOnLongPress,
                description = decrementDescription,
            ) {
                // Material Icons Core hat kein Minus; das typografische Minuszeichen passt zum "+".
                Text(
                    text = MINUS_SIGN,
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.clearAndSetSemantics { },
                )
            }
            val spoken = if (unit != null) "$value $unit" else value
            Row(
                modifier = Modifier
                    .widthIn(min = Sizing.stepperValueMinWidth)
                    .clearAndSetSemantics { contentDescription = spoken },
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.stepperValue,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.alignByBaseline(),
                )
                if (unit != null) {
                    Text(
                        text = " $unit",
                        style = MaterialTheme.typography.metricUnit,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }
            StepperButton(
                onClick = onIncrement,
                enabled = incrementEnabled,
                repeatOnLongPress = repeatOnLongPress,
                description = incrementDescription,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(Sizing.icon))
            }
        }
    }
}

private const val MINUS_SIGN = "−"

@Composable
private fun stepperDescription(res: Int, label: String?): String {
    val action = stringResource(res)
    return if (label != null) "$action: $label" else action
}

/** Verzögerung bis zur ersten Wiederholung beim Halten eines Stepper-Knopfs. */
internal const val STEPPER_REPEAT_INITIAL_DELAY_MILLIS = 500L

/** Pause zwischen den ersten Wiederholungen. */
internal const val STEPPER_REPEAT_START_INTERVAL_MILLIS = 250L

/** Schnellste Pause zwischen zwei Wiederholungen. */
internal const val STEPPER_REPEAT_MIN_INTERVAL_MILLIS = 50L

private const val STEPPER_REPEAT_ACCELERATION = 0.8f

/** Pause nach einer Wiederholung: jedes Mal kürzer, nie unter [STEPPER_REPEAT_MIN_INTERVAL_MILLIS]. */
internal fun nextStepperRepeatDelayMillis(currentMillis: Long): Long =
    (currentMillis * STEPPER_REPEAT_ACCELERATION).toLong().coerceAtLeast(STEPPER_REPEAT_MIN_INTERVAL_MILLIS)

@Composable
private fun StepperButton(
    onClick: () -> Unit,
    enabled: Boolean,
    repeatOnLongPress: Boolean,
    description: String,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val currentOnClick by rememberUpdatedState(onClick)
    // Nach Wiederholungen darf das Loslassen nicht noch einen Klick auslösen.
    val repeated = remember { booleanArrayOf(false) }
    LaunchedEffect(pressed, enabled, repeatOnLongPress) {
        if (pressed) repeated[0] = false
        if (pressed && enabled && repeatOnLongPress) {
            delay(STEPPER_REPEAT_INITIAL_DELAY_MILLIS)
            var interval = STEPPER_REPEAT_START_INTERVAL_MILLIS
            while (true) {
                repeated[0] = true
                currentOnClick()
                delay(interval)
                interval = nextStepperRepeatDelayMillis(interval)
            }
        }
    }
    FilledIconButton(
        onClick = {
            if (repeated[0]) repeated[0] = false else onClick()
        },
        enabled = enabled,
        interactionSource = interactionSource,
        shape = ExpressiveShapes.full,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
        ),
        modifier = Modifier
            .size(Sizing.minTouchTarget)
            .semantics { contentDescription = description },
        content = content,
    )
}
