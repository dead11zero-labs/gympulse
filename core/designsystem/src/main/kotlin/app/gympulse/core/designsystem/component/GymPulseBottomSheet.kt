package app.gympulse.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.Spacing

/**
 * Modales Bottom Sheet im GymPulse-Stil: `surfaceContainerLow`, große obere Ecken
 * (Material-Standard 28 dp), Drag-Handle, optionaler Titel, Inhalt mit Seitenrand und
 * Aktionsleiste unten. System-Insets behandelt [ModalBottomSheet] selbst. Auf großen Screens
 * auf [Sizing.sheetMaxWidth] begrenzt.
 *
 * Typische Nutzung: Satz bearbeiten, Übung auswählen, Filter.
 *
 * @param onDismissRequest Wischen nach unten, Tippen auf den Scrim oder Zurück.
 * @param title optionaler Titel (Title Large), als Überschrift markiert.
 * @param sheetState Standard: ohne halb geöffneten Zustand.
 * @param actions optionale Aktionen unten rechts (z. B. "Abbrechen", "Speichern").
 * @param content Inhalt in einer Spalte mit Seitenrand; für lange Listen eine `LazyColumn`
 *   mit `Modifier.weight(1f, fill = false)` verwenden.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GymPulseBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = Sizing.sheetMaxWidth,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = Spacing.l)
                        .semantics { heading() },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = Spacing.l),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                content = content,
            )
            if (actions != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.l),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}
