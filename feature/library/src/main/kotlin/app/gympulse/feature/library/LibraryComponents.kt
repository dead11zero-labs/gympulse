package app.gympulse.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.data.ui.labelRes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.EquipmentType
import app.gympulse.core.model.ExerciseCategory
import app.gympulse.core.model.MuscleGroup
import app.gympulse.core.model.TrackingType
import java.text.NumberFormat
import java.util.Locale

/** Zentrierte Inhaltsspalte mit maximaler Breite für große Bildschirme. */
@Composable
internal fun ContentColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxSize(),
            content = content,
        )
    }
}

/** Scrollbare Formularspalte mit Seitenrand und maximaler Breite. */
@Composable
internal fun FormColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
            content = content,
        )
    }
}

@Composable
internal fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
internal fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

/** Zeile "Bezeichnung  Wert" für Detailangaben. */
@Composable
internal fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(2f),
        )
    }
}

@Composable
internal fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_clear_search))
                }
            }
        },
    )
}

/** Filter-Chip, der ein Auswahlmenü öffnet; ohne Auswahl zeigt er [title], sonst den gewählten Eintrag. */
@Composable
internal fun <T : Any> FilterDropdownChip(
    title: String,
    allLabel: String,
    selected: T?,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(if (selected != null) optionLabel(selected) else title) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = {
                    onSelected(null)
                    expanded = false
                },
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
internal fun appLocale(): Locale = LocalConfiguration.current.locales[0]

/** Sprachcode der App, z. B. "de". */
@Composable
internal fun appLanguage(): String = appLocale().language

/** Zahl in der Zahlenschreibweise der App-Sprache, höchstens zwei Nachkommastellen. */
internal fun formatNumber(value: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }.format(value)

internal fun ExerciseCategory.categoryLabelRes(): Int = when (this) {
    ExerciseCategory.STRENGTH -> R.string.library_category_strength
    ExerciseCategory.STRETCHING -> R.string.library_category_stretching
    ExerciseCategory.PLYOMETRICS -> R.string.library_category_plyometrics
    ExerciseCategory.POWERLIFTING -> R.string.library_category_powerlifting
    ExerciseCategory.OLYMPIC_WEIGHTLIFTING -> R.string.library_category_olympic_weightlifting
    ExerciseCategory.STRONGMAN -> R.string.library_category_strongman
    ExerciseCategory.CARDIO -> R.string.library_category_cardio
}

internal fun TrackingType.trackingLabelRes(): Int = when (this) {
    TrackingType.WEIGHT_REPS -> R.string.library_tracking_weight_reps
    TrackingType.REPS -> R.string.library_tracking_reps
    TrackingType.DURATION -> R.string.library_tracking_duration
    TrackingType.DISTANCE -> R.string.library_tracking_distance
}

/** Lokalisierte Namen als kommagetrennte Liste, z. B. "Brust, Trizeps". */
@Composable
internal fun muscleNames(muscles: List<MuscleGroup>): String =
    muscles.map { stringResource(it.labelRes()) }.joinToString(", ")

@Composable
internal fun equipmentTypeName(type: EquipmentType): String = stringResource(type.labelRes())
