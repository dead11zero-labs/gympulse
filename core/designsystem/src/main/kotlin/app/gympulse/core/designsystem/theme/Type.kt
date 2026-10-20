package app.gympulse.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/*
 * Material-3-Typografie (Roboto/Systemschrift) mit klarer Hierarchie:
 * - Display/Headline für Kennzahlen und Screen-Titel,
 * - Title (Medium) für Karten und Abschnitte,
 * - Body für Fließtext, Label für Buttons, Chips und Metadaten.
 * Eigene Schriften kommen mit dem finalen Design von Jörg hier hinein.
 */

private val Base = Typography()

internal val GymPulseTypography = Base.copy(
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Medium),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Medium),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.Medium),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.Medium),
)

/** Tabellenziffern: gleich breite Ziffern, damit sich Zahlen beim Hochzählen nicht verschieben. */
private const val TABULAR_FIGURES = "tnum"

private fun TextStyle.emphasizedNumber(): TextStyle = copy(
    fontWeight = FontWeight.Bold,
    fontFeatureSettings = TABULAR_FIGURES,
    letterSpacing = (-0.01).em,
)

/*
 * Hervorgehobene Zahlenstile (M3 Expressive "Emphasized") für Kennzahlen. Sie leiten sich aus
 * der aktiven MaterialTheme.typography ab, folgen also späteren Schriftwechseln automatisch.
 * Nutzung: `style = MaterialTheme.typography.metricLarge`.
 */

/** Sehr große Kennzahl, z. B. Hero-Wert auf dem Dashboard oder Timer (Display Large, fett). */
val Typography.metricHero: TextStyle get() = displayLarge.emphasizedNumber()

/** Große Kennzahl in einer [app.gympulse.core.designsystem.component.StatTile] (Display Small, fett). */
val Typography.metricLarge: TextStyle get() = displaySmall.emphasizedNumber()

/** Mittlere Kennzahl, z. B. in kompakten Kacheln oder im Fortschrittsring (Headline Medium, fett). */
val Typography.metricMedium: TextStyle get() = headlineMedium.emphasizedNumber()

/** Kleine Kennzahl in Listen oder Satzzeilen (Title Large, fett). */
val Typography.metricSmall: TextStyle get() = titleLarge.emphasizedNumber()

/** Einheit neben einer Kennzahl ("kg", "min"), gedämpft und kleiner als die Zahl. */
val Typography.metricUnit: TextStyle get() = titleMedium.copy(fontFeatureSettings = TABULAR_FIGURES)
