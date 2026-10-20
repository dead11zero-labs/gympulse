package app.gympulse.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Horizontal scrollbare Reihe aus Material-3-[FilterChip]s, z. B. für Muskelgruppen,
 * Zeiträume ("Woche", "Monat", "Jahr") oder Equipment. Ausgewählte Chips zeigen ein Häkchen.
 * Funktioniert für Einfach- und Mehrfachauswahl; die Auswahl hält der Aufrufer.
 *
 * Beispiel Einfachauswahl:
 * ```
 * ChipRow(
 *     items = Period.entries,
 *     isSelected = { it == state.period },
 *     onClick = viewModel::selectPeriod,
 *     label = { stringResource(it.labelRes) },
 * )
 * ```
 *
 * @param items Einträge in Anzeigereihenfolge.
 * @param isSelected ob ein Eintrag ausgewählt ist.
 * @param onClick Klick auf einen Eintrag (umschalten bzw. auswählen).
 * @param label Beschriftung eines Eintrags.
 * @param key stabiler Schlüssel je Eintrag für die LazyRow; Standard: der Eintrag selbst.
 * @param contentPadding Standard: Seitenrand links und rechts, damit Chips bündig mit dem
 *   übrigen Inhalt beginnen und beim Scrollen bis an den Rand laufen.
 */
@Composable
fun <T> ChipRow(
    items: List<T>,
    isSelected: (T) -> Boolean,
    onClick: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = Spacing.screenMargin),
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(items = items, key = key) { item ->
            val selected = isSelected(item)
            FilterChip(
                selected = selected,
                onClick = { onClick(item) },
                label = { Text(label(item)) },
                enabled = enabled,
                leadingIcon = if (selected) {
                    @Composable {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}
