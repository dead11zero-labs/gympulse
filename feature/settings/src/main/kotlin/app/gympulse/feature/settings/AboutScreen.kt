package app.gympulse.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.gympulse.core.designsystem.theme.Spacing

/** "Über die App": Version sowie Quellen der Übungsdaten und Bibliotheken. */
@Composable
internal fun AboutScreen(modifier: Modifier = Modifier) {
    val version = appVersion(LocalContext.current)

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = Spacing.maxContentWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screenMargin, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.about_app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            LicenseSection(
                title = stringResource(R.string.about_section_exercise_data),
                entries = LicenseSources.exerciseData,
            )
            LicenseSection(
                title = stringResource(R.string.about_section_libraries),
                entries = LicenseSources.libraries,
            )
        }
    }
}

@Composable
private fun LicenseSection(title: String, entries: List<LicenseEntry>) {
    SettingsSection(title) {
        entries.forEachIndexed { index, entry ->
            if (index > 0) SettingsDivider()
            val licenseLine = stringResource(R.string.about_license, entry.license)
            val supporting = entry.descriptionRes
                ?.let { "${stringResource(it)}\n$licenseLine" }
                ?: licenseLine
            SettingsRow(headline = entry.name, supporting = supporting)
        }
    }
}
