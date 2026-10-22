package app.gympulse.core.insightcards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Abschnitt mit Insight-Karten oder, wenn es keine gibt, einem Leerzustand. Bei [InsightsUi.Loading]
 * (weder Karten noch Grund) bleibt der Abschnitt leer.
 *
 * @param forExercise Darstellung im Übungsdetail: kürzere Überschrift und eigener Leerzustandstext.
 */
@Composable
fun InsightsSection(
    insights: InsightsUi,
    modifier: Modifier = Modifier,
    forExercise: Boolean = false,
) {
    if (insights.isEmpty && insights.emptyReason == null) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = stringResource(
                if (forExercise) R.string.insights_section_title_exercise else R.string.insights_section_title,
            ),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        if (insights.isEmpty) {
            InsightsEmptyCard(reason = insights.emptyReason, forExercise = forExercise)
        } else {
            insights.cards.forEach { card -> InsightCard(card) }
            Text(
                text = stringResource(R.string.insights_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun InsightCard(card: InsightCardUi, modifier: Modifier = Modifier) {
    GymPulseCard(
        modifier = modifier.fillMaxWidth(),
        variant = card.tone.cardVariant(),
        verticalSpacing = Spacing.xxs,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = card.tone.icon(),
                contentDescription = null,
                modifier = Modifier.size(Sizing.icon),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                card.subject?.let { subject ->
                    Text(
                        text = subject.resolve(),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 2,
                    )
                }
                Text(text = card.title.resolve(), style = MaterialTheme.typography.titleMedium)
                Text(text = card.message.resolve(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun InsightsEmptyCard(reason: InsightsEmptyReason?, forExercise: Boolean) {
    val (titleRes, messageRes) = when (reason) {
        InsightsEmptyReason.NOTHING_NOTABLE ->
            R.string.insights_empty_notable_title to R.string.insights_empty_notable_message
        else -> R.string.insights_empty_not_enough_title to if (forExercise) {
            R.string.insights_empty_exercise_message
        } else {
            R.string.insights_empty_not_enough_message
        }
    }
    GymPulseCard(
        modifier = Modifier.fillMaxWidth(),
        variant = GymPulseCardVariant.Level1,
        verticalSpacing = Spacing.xxs,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizing.icon),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(messageRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun InsightTone.cardVariant(): GymPulseCardVariant = when (this) {
    InsightTone.POSITIVE -> GymPulseCardVariant.Tertiary
    InsightTone.NEUTRAL -> GymPulseCardVariant.Filled
    InsightTone.ATTENTION -> GymPulseCardVariant.Secondary
}

private fun InsightTone.icon(): ImageVector = when (this) {
    InsightTone.POSITIVE -> Icons.Filled.Check
    InsightTone.NEUTRAL -> Icons.Filled.Info
    InsightTone.ATTENTION -> Icons.Filled.Warning
}

/** Löst einen [InsightText] samt verschachtelter Argumente zu einem String auf. */
@Composable
internal fun InsightText.resolve(): String = when (this) {
    is InsightText.Plain -> value
    is InsightText.Res -> {
        val resolvedArgs = args.map { if (it is InsightText) it.resolve() else it }
        stringResource(id, *resolvedArgs.toTypedArray())
    }
}
