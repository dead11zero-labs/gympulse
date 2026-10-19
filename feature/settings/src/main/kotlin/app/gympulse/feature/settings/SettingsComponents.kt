package app.gympulse.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import app.gympulse.core.designsystem.theme.Spacing

/** Überschrift einer Einstellungsgruppe. */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs),
    )
}

/** Karte mit den Zeilen einer Gruppe; zwischen den Zeilen steht ein Trenner. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        content()
    }
}

@Composable
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.m),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Eine Einstellungszeile. Ohne [onClick] ist sie nicht bedienbar (z. B. "Kommt bald").
 */
@Composable
internal fun SettingsRow(
    headline: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { text -> { Text(text) } },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.then(clickable),
    )
}

/** Zeile für Funktionen, die noch nicht umgesetzt sind. */
@Composable
internal fun ComingSoonRow(headline: String, supporting: String, modifier: Modifier = Modifier) {
    SettingsRow(
        headline = headline,
        supporting = supporting,
        modifier = modifier,
        trailing = {
            Text(
                text = stringResource(R.string.settings_coming_soon),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** Dialog mit Einfachauswahl (Radio-Buttons). */
@Composable
internal fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(option) },
                            )
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = Spacing.m),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) }
        },
    )
}

/**
 * Dialog mit einem Textfeld. [validate] prüft die Eingabe; "Speichern" ist nur bei gültiger
 * Eingabe aktiv, ein Fehlertext erscheint, sobald etwas Ungültiges getippt wurde.
 */
@Composable
internal fun <T> ValueInputDialog(
    title: String,
    label: String,
    initialValue: String,
    errorMessage: String,
    keyboardType: KeyboardType,
    validate: (String) -> Validated<T>,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
    suffix: String? = null,
    supportingText: String? = null,
) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    val result = validate(text)
    val showError = result is Validated.Invalid && text.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                suffix = suffix?.let { s -> { Text(s) } },
                singleLine = true,
                isError = showError,
                supportingText = { Text(if (showError) errorMessage else supportingText.orEmpty()) },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (result is Validated.Ok) onConfirm(result.value) },
                enabled = result is Validated.Ok,
            ) { Text(stringResource(R.string.settings_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) }
        },
    )
}
