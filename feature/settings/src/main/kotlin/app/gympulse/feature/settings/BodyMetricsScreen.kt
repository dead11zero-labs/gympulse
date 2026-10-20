package app.gympulse.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.adaptive.currentWindowWidthClass
import app.gympulse.core.designsystem.component.ChipRow
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.component.GymPulseCard
import app.gympulse.core.designsystem.component.GymPulseCardVariant
import app.gympulse.core.designsystem.component.ListRow
import app.gympulse.core.designsystem.component.StatTile
import app.gympulse.core.designsystem.component.StatTileStyle
import app.gympulse.core.designsystem.component.StatTrend
import app.gympulse.core.designsystem.component.TrendDirection
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.BodyMeasurement
import app.gympulse.core.model.MeasurementType
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt

/** Erfassung, Verlauf und Diagramm der Körpermaße. */
@Composable
internal fun BodyMetricsScreen(
    modifier: Modifier = Modifier,
    viewModel: BodyMetricsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BodyMeasurement?>(null) }
    var deleting by remember { mutableStateOf<BodyMeasurement?>(null) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = currentWindowWidthClass().horizontalMargin,
                vertical = Spacing.m,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            item {
                Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.body_add))
                }
            }
            item {
                TypeChips(
                    selected = state.selectedType,
                    onSelect = viewModel::selectType,
                    modifier = Modifier.padding(horizontal = Spacing.none),
                )
            }
            item { SummaryCard(state) }
            item { ChartCard(state) }
            item {
                SettingsSectionHeader(stringResource(R.string.body_history))
            }
            if (state.history.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Face,
                        title = stringResource(R.string.body_history),
                        message = stringResource(R.string.body_empty),
                        actionLabel = stringResource(R.string.body_add),
                        onAction = { adding = true },
                    )
                }
            } else {
                items(state.history, key = { it.id }) { entry ->
                    HistoryRow(
                        modifier = Modifier.animateItem(),
                        entry = entry,
                        onEdit = { editing = entry },
                        onDelete = { deleting = entry },
                    )
                }
            }
        }
    }

    if (adding) {
        AddMeasurementDialog(
            onConfirm = { values ->
                viewModel.add(values)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    editing?.let { entry ->
        ValueInputDialog(
            title = stringResource(R.string.body_edit_title, typeLabel(entry.type)),
            label = typeLabel(entry.type),
            initialValue = formatNumber(entry.value),
            errorMessage = errorMessage(entry.type),
            keyboardType = KeyboardType.Decimal,
            validate = { InputValidation.measurement(entry.type, it) },
            onConfirm = {
                viewModel.updateValue(entry.id, it)
                editing = null
            },
            onDismiss = { editing = null },
            suffix = unitLabel(entry.type),
        )
    }
    deleting?.let { entry ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.body_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.body_delete_message,
                        formatValue(entry.type, entry.value),
                        formatDate(entry),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(entry.id)
                        deleting = null
                    },
                ) { Text(stringResource(R.string.body_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.settings_action_cancel)) }
            },
        )
    }
}

@Composable
private fun TypeChips(selected: MeasurementType, onSelect: (MeasurementType) -> Unit, modifier: Modifier = Modifier) {
    ChipRow(
        items = MeasurementType.entries,
        isSelected = { it == selected },
        onClick = onSelect,
        label = { typeLabel(it) },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = Spacing.none),
    )
}

@Composable
private fun SummaryCard(state: BodyMetricsUiState) {
    val latest = state.history.firstOrNull() ?: return
    val trend = state.change?.let { change ->
        StatTrend(
            label = stringResource(R.string.body_change, formatSigned(latest.type, change)),
            direction = when {
                change > 0 -> TrendDirection.Up
                change < 0 -> TrendDirection.Down
                else -> TrendDirection.Flat
            },
        )
    }
    StatTile(
        value = formatValue(latest.type, latest.value),
        label = stringResource(R.string.body_latest),
        trend = trend,
        style = StatTileStyle.Highlighted,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
    )
}

@Composable
private fun ChartCard(state: BodyMetricsUiState) {
    GymPulseCard(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        variant = GymPulseCardVariant.Elevated,
        shape = ExpressiveShapes.largeIncreased,
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.chart.size < 2) {
                Text(
                    text = stringResource(R.string.body_chart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = Spacing.l),
                )
            } else {
                MeasurementChart(state.chart)
            }
        }
    }
}

/** Liniendiagramm; die X-Achse nutzt den Index, damit mehrere Messungen pro Tag nicht kollidieren. */
@Composable
private fun MeasurementChart(entries: List<BodyMeasurement>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(entries) {
        modelProducer.runTransaction {
            lineModel { series(entries.indices.toList(), entries.map { it.value }) }
        }
    }
    val dateFormatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT) }
    val zone = remember { ZoneId.systemDefault() }
    val axisFormatter = remember(entries) {
        CartesianValueFormatter { _, x, _ ->
            entries.getOrNull(x.roundToInt())
                ?.let { dateFormatter.format(it.measuredAt.atZone(zone)) }
                .orEmpty()
        }
    }
    val description = stringResource(R.string.body_chart_description, typeLabel(entries.first().type))
    ProvideVicoTheme(rememberM3VicoTheme()) {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = axisFormatter),
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        )
    }
}

@Composable
private fun HistoryRow(
    entry: BodyMeasurement,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListRow(
            headline = formatValue(entry.type, entry.value),
            supportingText = formatDate(entry),
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.body_edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.body_delete))
                    }
                }
            },
        )
    }
}

/** Formular für eine neue Messung: Gewicht ist Pflicht, alle anderen Felder sind optional. */
@Composable
private fun AddMeasurementDialog(
    onConfirm: (Map<MeasurementType, Double>) -> Unit,
    onDismiss: () -> Unit,
) {
    val types = MeasurementType.entries
    val texts = types.map { rememberSaveable { mutableStateOf("") } }
    val results: List<Validated<Double?>> = types.mapIndexed { index, type ->
        val text = texts[index].value
        if (type == MeasurementType.WEIGHT) {
            InputValidation.measurement(type, text)
        } else {
            InputValidation.optionalMeasurement(type, text)
        }
    }
    val valid = results.all { it is Validated.Ok }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.body_add_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                Text(
                    text = stringResource(R.string.body_add_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                types.forEachIndexed { index, type ->
                    val showError = results[index] is Validated.Invalid && texts[index].value.isNotEmpty()
                    OutlinedTextField(
                        value = texts[index].value,
                        onValueChange = { texts[index].value = it },
                        label = { Text(typeLabel(type)) },
                        suffix = { Text(unitLabel(type)) },
                        singleLine = true,
                        isError = showError,
                        supportingText = if (showError) {
                            { Text(errorMessage(type)) }
                        } else {
                            null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val values = buildMap {
                        types.forEachIndexed { index, type ->
                            ((results[index] as? Validated.Ok)?.value)?.let { put(type, it) }
                        }
                    }
                    onConfirm(values)
                },
            ) { Text(stringResource(R.string.settings_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) }
        },
    )
}

@Composable
private fun typeLabel(type: MeasurementType): String = stringResource(
    when (type) {
        MeasurementType.WEIGHT -> R.string.body_type_weight
        MeasurementType.BODY_FAT -> R.string.body_type_body_fat
        MeasurementType.CHEST -> R.string.body_type_chest
        MeasurementType.WAIST -> R.string.body_type_waist
        MeasurementType.HIPS -> R.string.body_type_hips
        MeasurementType.UPPER_ARM -> R.string.body_type_upper_arm
        MeasurementType.THIGH -> R.string.body_type_thigh
        MeasurementType.CALF -> R.string.body_type_calf
        MeasurementType.NECK -> R.string.body_type_neck
    },
)

@Composable
private fun unitLabel(type: MeasurementType): String = stringResource(
    when (type) {
        MeasurementType.WEIGHT -> R.string.settings_unit_kg
        MeasurementType.BODY_FAT -> R.string.body_unit_percent
        else -> R.string.settings_unit_cm
    },
)

@Composable
private fun errorMessage(type: MeasurementType): String = stringResource(
    when (type) {
        MeasurementType.WEIGHT -> R.string.settings_error_weight
        MeasurementType.BODY_FAT -> R.string.body_error_body_fat
        else -> R.string.body_error_circumference
    },
)

@Composable
private fun formatValue(type: MeasurementType, value: Double): String = stringResource(
    when (type) {
        MeasurementType.WEIGHT -> R.string.settings_value_kg
        MeasurementType.BODY_FAT -> R.string.body_value_percent
        else -> R.string.settings_value_cm
    },
    formatNumber(value),
)

/** Wie [formatValue], mit Vorzeichen (+/−) für Differenzen. */
@Composable
private fun formatSigned(type: MeasurementType, value: Double): String {
    val text = formatValue(type, kotlin.math.abs(value))
    return when {
        value > 0 -> "+$text"
        value < 0 -> "−$text"
        else -> text
    }
}

@Composable
private fun formatDate(entry: BodyMeasurement): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) }
    return formatter.format(entry.measuredAt.atZone(ZoneId.systemDefault()))
}
