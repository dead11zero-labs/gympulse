package app.gympulse.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.gympulse.core.designsystem.R
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing
import kotlin.math.roundToInt

/** Reine Hilfsfunktionen für Fortschrittsanzeigen. */
object ProgressMath {
    /**
     * Anteil von [current] an [target], begrenzt auf 0..1. Ein Ziel ≤ 0 ergibt 0, damit leere
     * Pläne oder fehlende Ziele keinen vollen Ring zeigen. NaN wird als 0 behandelt.
     */
    fun fraction(current: Double, target: Double): Float {
        if (target <= 0.0 || current.isNaN() || target.isNaN()) return 0f
        return (current / target).coerceIn(0.0, 1.0).toFloat()
    }

    /** Begrenzt einen Fortschritt auf 0..1 (NaN wird 0). */
    fun clamp(progress: Float): Float = if (progress.isNaN()) 0f else progress.coerceIn(0f, 1f)

    /** Ganzzahliger Prozentwert 0..100 für Beschriftungen und TalkBack. */
    fun percent(progress: Float): Int = (clamp(progress) * 100).roundToInt()
}

/**
 * Bestimmter Fortschrittsring (M3 Circular Progress Indicator) mit dicker, abgerundeter Linie
 * und frei belegbarer Mitte, z. B. für Wochenziel oder Trainingsfortschritt.
 *
 * TalkBack liest den Prozentwert bzw. [contentDescription] vor; die Mitte wird nicht separat
 * vorgelesen, wenn [contentDescription] gesetzt ist.
 *
 * @param progress Fortschritt 0..1 (wird begrenzt), siehe [ProgressMath.fraction].
 * @param size Durchmesser, Standard [Sizing.progressRing].
 * @param strokeWidth Linienstärke, Standard [Sizing.progressRingStroke].
 * @param color Farbe des Fortschritts, Standard `primary`.
 * @param trackColor Farbe der Spur, Standard `secondaryContainer`.
 * @param animate Änderungen weich animieren.
 * @param contentDescription optionaler Text für TalkBack statt "NN Prozent".
 * @param content Inhalt der Mitte, z. B. eine Zahl in `metricMedium`.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = Sizing.progressRing,
    strokeWidth: Dp = Sizing.progressRingStroke,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    animate: Boolean = true,
    contentDescription: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val target = ProgressMath.clamp(progress)
    val animated by animateFloatAsState(targetValue = target, label = "progressRing")
    val shown = if (animate) animated else target
    val description = contentDescription
        ?: stringResource(R.string.designsystem_progress_percent, ProgressMath.percent(target))
    Box(
        modifier = modifier
            .size(size)
            .semantics(mergeDescendants = true) { this.contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { shown },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = strokeWidth,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
        )
        content()
    }
}

/**
 * Dicker, abgerundeter Fortschrittsbalken (M3 Linear Progress Indicator) mit optionaler
 * Beschriftung links und Wert rechts darüber, z. B. "Volumen" – "8 / 12 Sätze".
 *
 * @param progress Fortschritt 0..1 (wird begrenzt).
 * @param label optionale Beschriftung links über dem Balken.
 * @param valueText optionaler Wert rechts über dem Balken.
 * @param height Balkenhöhe, Standard [Sizing.progressBar].
 * @param color Farbe des Fortschritts, Standard `primary`.
 * @param trackColor Farbe der Spur, Standard `secondaryContainer`.
 * @param animate Änderungen weich animieren.
 */
@Composable
fun GymPulseProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
    valueText: String? = null,
    height: Dp = Sizing.progressBar,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    animate: Boolean = true,
) {
    val target = ProgressMath.clamp(progress)
    val animated by animateFloatAsState(targetValue = target, label = "progressBar")
    val shown = if (animate) animated else target
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        if (label != null || valueText != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (valueText != null) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        LinearProgressIndicator(
            progress = { shown },
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
            color = color,
            trackColor = trackColor,
            strokeCap = StrokeCap.Round,
        )
    }
}
