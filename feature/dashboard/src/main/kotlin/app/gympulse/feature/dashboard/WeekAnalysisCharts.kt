package app.gympulse.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.designsystem.theme.Spacing
import kotlin.math.max

private const val REVEAL_MILLIS = 800
private const val BAR_WIDTH_FRACTION = 0.5f
private const val RING_STROKE_FRACTION = 0.16f
private const val RING_GAP_DEGREES = 3f
private const val FULL_CIRCLE = 360f
private const val RING_START_DEGREES = -90f
private const val LINE_TOP_PADDING_FRACTION = 0.12f
private const val FILL_ALPHA = 0.35f

/** Fortschritt 0..1, der bei jedem neuen [key] von vorn läuft; Diagramme wachsen damit ein. */
@Composable
internal fun rememberReveal(key: Any?): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(REVEAL_MILLIS, easing = FastOutSlowInEasing))
    }
    return progress.value
}

/** Farbe je Körperregion; dieselbe in Ring, Legende und Muskelbalken. */
@Composable
internal fun regionColor(region: BodyRegion): Color = when (region) {
    BodyRegion.UPPER -> MaterialTheme.colorScheme.primary
    BodyRegion.LEGS -> MaterialTheme.colorScheme.secondary
    BodyRegion.CORE -> MaterialTheme.colorScheme.tertiary
}

/**
 * Tagesbalken der Woche. Ein Tipp wählt einen Tag ([onSelectDay]); der gewählte Balken ist
 * `primary`, die übrigen `primaryContainer`. Screenreader lesen nur [description].
 */
@Composable
internal fun DailyBarsChart(
    dailyKg: List<Double>,
    dayLabels: List<String>,
    selectedDay: Int?,
    reveal: Float,
    description: String,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.primaryContainer
    val emptyColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val currentOnSelect by rememberUpdatedState(onSelectDay)
    val maxKg = dailyKg.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.xxl * 3)
                .pointerInput(dailyKg.size) {
                    detectTapGestures { offset ->
                        val index = (offset.x / size.width * dailyKg.size).toInt().coerceIn(0, dailyKg.size - 1)
                        currentOnSelect(index)
                    }
                },
        ) {
            val slot = size.width / dailyKg.size
            val barWidth = slot * BAR_WIDTH_FRACTION
            val corner = CornerRadius(barWidth / 2f)
            dailyKg.forEachIndexed { index, value ->
                val left = slot * index + (slot - barWidth) / 2f
                val height = if (value > 0.0) {
                    max(size.height * (value / maxKg).toFloat(), barWidth) * reveal
                } else {
                    barWidth * reveal
                }
                drawRoundRect(
                    color = when {
                        value <= 0.0 -> emptyColor
                        index == selectedDay -> selectedColor
                        else -> barColor
                    },
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = corner,
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            dayLabels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (index == selectedDay) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == selectedDay) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Eine Zeile der Muskelgruppen-Balkenliste. */
internal data class MuscleBarEntry(
    val label: String,
    val sets: Double,
    val region: BodyRegion,
)

/** Waagerechte Balken je Muskelgruppe, eingefärbt nach Körperregion; der längste Balken füllt die Breite. */
@Composable
internal fun MuscleBars(
    entries: List<MuscleBarEntry>,
    formatSets: (Double) -> String,
    reveal: Float,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val maxSets = entries.maxOfOrNull { it.sets }?.takeIf { it > 0.0 } ?: 1.0
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        entries.forEach { entry ->
            val color = regionColor(entry.region)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatSets(entry.sets),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(Spacing.xs)) {
                    val corner = CornerRadius(size.height / 2f)
                    drawRoundRect(color = track, size = size, cornerRadius = corner)
                    val width = max(size.width * (entry.sets / maxSets).toFloat() * reveal, size.height)
                    drawRoundRect(color = color, size = Size(width, size.height), cornerRadius = corner)
                }
            }
        }
    }
}

/** Ring mit den Anteilen der Körperregionen; die Segmente laufen beim Einblenden im Uhrzeigersinn ein. */
@Composable
internal fun RegionRing(
    shares: Map<BodyRegion, Float>,
    reveal: Float,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val colors = BodyRegion.entries.associateWith { regionColor(it) }
    Canvas(modifier = modifier.size(Spacing.xxxl * 2)) {
        val strokeWidth = size.minDimension * RING_STROKE_FRACTION
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = FULL_CIRCLE,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth),
        )
        var start = RING_START_DEGREES
        BodyRegion.entries.forEach { region ->
            val share = shares[region] ?: 0f
            if (share <= 0f) return@forEach
            val sweep = share * FULL_CIRCLE
            drawArc(
                color = colors.getValue(region),
                startAngle = start + RING_GAP_DEGREES / 2f,
                sweepAngle = (sweep - RING_GAP_DEGREES).coerceAtLeast(1f) * reveal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )
            start += sweep
        }
    }
}

/** Geteilter Balken Kraft | Kardio; [cardioShare] ist der Kardio-Anteil 0..1. */
@Composable
internal fun SplitBar(
    cardioShare: Float,
    reveal: Float,
    modifier: Modifier = Modifier,
) {
    val strengthColor = MaterialTheme.colorScheme.primary
    val cardioColor = MaterialTheme.colorScheme.tertiary
    Canvas(modifier = modifier.fillMaxWidth().height(Spacing.m)) {
        val corner = CornerRadius(size.height / 2f)
        val gap = size.height / 4f
        val share = (cardioShare * reveal).coerceIn(0f, 1f)
        if (share <= 0f) {
            drawRoundRect(color = strengthColor, size = size, cornerRadius = corner)
            return@Canvas
        }
        val split = size.width * (1f - share)
        val strengthWidth = split - gap / 2f
        if (strengthWidth > 0f) {
            drawRoundRect(color = strengthColor, size = Size(strengthWidth, size.height), cornerRadius = corner)
        }
        val cardioLeft = max(split + gap / 2f, 0f)
        drawRoundRect(
            color = cardioColor,
            topLeft = Offset(cardioLeft, 0f),
            size = Size(size.width - cardioLeft, size.height),
            cornerRadius = corner,
        )
    }
}

/** Farbpunkt vor Legendeneinträgen. */
@Composable
internal fun LegendDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(Spacing.s)) {
        drawCircle(color = color)
    }
}

/**
 * Verlauf als geglättete Linie mit Fläche. Die Punkte liegen mittig über den Beschriftungen;
 * ein Tipp wählt die nächste Woche ([onSelect]). Die Linie läuft beim Einblenden von links ein.
 *
 * @param labels Beschriftung je Wert; leere Texte lassen die Stelle frei.
 */
@Composable
internal fun TrendLineChart(
    values: List<Double>,
    labels: List<String>,
    selectedIndex: Int,
    reveal: Float,
    description: String,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val haloColor = MaterialTheme.colorScheme.surfaceContainer
    val currentOnSelect by rememberUpdatedState(onSelect)
    val maxValue = values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.xxl * 3)
                .pointerInput(values.size) {
                    detectTapGestures { offset ->
                        currentOnSelect((offset.x / size.width * values.size).toInt().coerceIn(0, values.size - 1))
                    }
                },
        ) {
            val slot = size.width / values.size
            val top = size.height * LINE_TOP_PADDING_FRACTION
            val points = values.mapIndexed { index, value ->
                Offset(
                    x = slot * index + slot / 2f,
                    y = top + (size.height - top) * (1f - (value / maxValue).toFloat()),
                )
            }
            val line = smoothPath(points)
            val area = smoothPath(points, closeAtY = size.height)
            clipRect(right = size.width * reveal) {
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = FILL_ALPHA), Color.Transparent)),
                )
                drawPath(
                    path = line,
                    color = lineColor,
                    style = Stroke(width = Spacing.xxs.toPx(), cap = StrokeCap.Round),
                )
                points.forEachIndexed { index, point ->
                    if (index == selectedIndex) {
                        drawLine(guideColor, point, Offset(point.x, size.height), strokeWidth = Spacing.xxs.toPx() / 2f)
                        drawCircle(haloColor, radius = Spacing.xs.toPx() + Spacing.xxs.toPx() / 2f, center = point)
                        drawCircle(lineColor, radius = Spacing.xs.toPx(), center = point)
                    } else {
                        drawCircle(lineColor, radius = Spacing.xxs.toPx(), center = point)
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == selectedIndex) lineColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Kubische Linie durch [points]; die Kontrollpunkte liegen waagerecht, damit nichts überschwingt.
 * Mit [closeAtY] schließt der Pfad zu einer Fläche bis zu dieser Höhe.
 */
private fun smoothPath(points: List<Offset>, closeAtY: Float? = null): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        val midX = (previous.x + current.x) / 2f
        cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
    }
    if (closeAtY != null) {
        lineTo(points.last().x, closeAtY)
        lineTo(points.first().x, closeAtY)
        close()
    }
}
