package app.gympulse.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.gympulse.core.designsystem.R

/*
 * Kinetic-Material-Typografie (DESIGN.md):
 * - Plus Jakarta Sans für Display, Headline, Title und Label (geometrisch, kräftig),
 * - Roboto Flex für Body (dichte Satz- und Metriktabellen).
 * Beide kommen als Google Downloadable Fonts über die Play-Services-Schriftquelle
 * (Zertifikate in res/values/font_certs.xml). Solange eine Schrift lädt oder wenn sie nicht
 * geladen werden kann (keine Play Services, offline beim ersten Start), rendert Compose mit der
 * Systemschrift (FontFamily.Default); die App bleibt damit voll offline nutzbar.
 */

private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private fun googleFamily(name: String, weights: List<FontWeight>): FontFamily {
    val font = GoogleFont(name)
    return FontFamily(weights.map { Font(googleFont = font, fontProvider = fontProvider, weight = it) })
}

/** Plus Jakarta Sans: Display, Headline, Title, Label. */
val PlusJakartaSans: FontFamily = googleFamily(
    "Plus Jakarta Sans",
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold),
)

/** Roboto Flex: Body. */
val RobotoFlex: FontFamily = googleFamily(
    "Roboto Flex",
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold),
)

private fun jakarta(size: Int, lineHeight: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = PlusJakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

private fun flex(size: Int, lineHeight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = RobotoFlex,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

/*
 * Zuordnung DESIGN.md -> Material 3:
 * display-lg -> displayLarge, display-lg-mobile -> displayMedium, displaySmall liegt dazwischen
 * zur Überleitung auf headline-lg. headline-lg/md/sm, title-lg/md/sm, body-lg/md/sm und
 * label-lg/md/sm entsprechen 1:1 ihren M3-Namen.
 */
internal val GymPulseTypography = Typography(
    displayLarge = jakarta(56, 64, FontWeight.ExtraBold, -0.02),
    displayMedium = jakarta(40, 48, FontWeight.ExtraBold, -0.02),
    displaySmall = jakarta(36, 44, FontWeight.ExtraBold, -0.02),
    headlineLarge = jakarta(32, 40, FontWeight.Bold, -0.01),
    headlineMedium = jakarta(22, 28, FontWeight.SemiBold),
    headlineSmall = jakarta(18, 24, FontWeight.SemiBold),
    titleLarge = jakarta(18, 24, FontWeight.Bold, 0.01),
    titleMedium = jakarta(16, 22, FontWeight.SemiBold, 0.01),
    titleSmall = jakarta(14, 20, FontWeight.SemiBold),
    bodyLarge = flex(16, 24, 0.01),
    bodyMedium = flex(14, 20),
    bodySmall = flex(12, 16),
    labelLarge = jakarta(14, 20, FontWeight.Bold, 0.03),
    labelMedium = jakarta(12, 16, FontWeight.SemiBold, 0.04),
    labelSmall = jakarta(10, 14, FontWeight.Bold, 0.06),
)

/**
 * Tabellenziffern (OpenType-Feature "tnum"): gleich breite Ziffern, damit Timer, Gewicht und
 * Wiederholungen beim Hochzählen nicht springen.
 */
object TabularNumbers {
    /** Wert für [TextStyle.fontFeatureSettings]. */
    const val FEATURE_SETTINGS: String = "tnum"
}

/** Derselbe Stil mit Tabellenziffern, z. B. `MaterialTheme.typography.bodyLarge.tabularNumbers()`. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = TabularNumbers.FEATURE_SETTINGS)

private fun TextStyle.emphasizedNumber(): TextStyle {
    val current = fontWeight ?: FontWeight.Normal
    return copy(
        fontWeight = if (current < FontWeight.Bold) FontWeight.Bold else current,
        fontFeatureSettings = TabularNumbers.FEATURE_SETTINGS,
        // Engere Laufweite des Ausgangsstils (z. B. Display -0.02 em) bleibt erhalten.
        letterSpacing = if (letterSpacing.type == TextUnitType.Em && letterSpacing.value < -0.01f) {
            letterSpacing
        } else {
            (-0.01).em
        },
    )
}

/*
 * Hervorgehobene Zahlenstile für Kennzahlen. Sie leiten sich aus der aktiven
 * MaterialTheme.typography ab. Nutzung: `style = MaterialTheme.typography.metricLarge`.
 */

/** Sehr große Kennzahl, z. B. Hero-Wert auf dem Dashboard oder Timer (Display Large). */
val Typography.metricHero: TextStyle get() = displayLarge.emphasizedNumber()

/** Große Kennzahl in einer [app.gympulse.core.designsystem.component.StatTile] (Display Small). */
val Typography.metricLarge: TextStyle get() = displaySmall.emphasizedNumber()

/** Mittlere Kennzahl, z. B. in kompakten Kacheln oder im Fortschrittsring (Headline Medium, fett). */
val Typography.metricMedium: TextStyle get() = headlineMedium.emphasizedNumber()

/** Kleine Kennzahl in Listen oder Satzzeilen (Title Large, fett). */
val Typography.metricSmall: TextStyle get() = titleLarge.emphasizedNumber()

/** Einheit neben einer Kennzahl ("kg", "min"), gedämpft und kleiner als die Zahl. */
val Typography.metricUnit: TextStyle get() = titleMedium.tabularNumbers()

/** Wert in einem [app.gympulse.core.designsystem.component.NumberStepper] (headline-md, tabellarisch). */
val Typography.stepperValue: TextStyle get() = headlineMedium.emphasizedNumber()

/** Wert in einer Satzzeile, z. B. "50" kg oder "12" Wdh. (title-md, tabellarisch). */
val Typography.setValue: TextStyle get() = titleMedium.tabularNumbers()

/** Großbuchstaben-Label über Kennzahlen ("GEWICHT (KG)", "WDH."), siehe LabelCaps. */
val Typography.labelCaps: TextStyle get() = labelSmall
