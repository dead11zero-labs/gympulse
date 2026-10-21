package app.gympulse.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.gympulse.core.designsystem.R
import app.gympulse.core.designsystem.theme.Sizing
import app.gympulse.core.designsystem.theme.tabularNumbers

/**
 * Satz abhaken (Kinetic Material, "Set Completion Indicators"): abgerundetes Quadrat
 * (40 dp, Ecken `shapes.medium` = 16 dp) in einem 48-dp-Touch-Ziel.
 *
 * - Offen: `surfaceContainerHigh` mit 1,5 dp `outline`-Rand, zeigt die Satznummer.
 * - Erledigt: gefüllt in `primary` mit `onPrimary`-Haken; beim Abhaken gibt es ein
 *   Bestätigungs-Haptik-Feedback.
 *
 * TalkBack liest "Satz 2, Kontrollkästchen, aktiviert".
 *
 * @param checked ob der Satz erledigt ist.
 * @param onCheckedChange neuer Zustand; `null` macht die Box rein anzeigend.
 * @param setNumber Satznummer im offenen Zustand, z. B. "2" (oder "W" für Aufwärmsätze).
 * @param contentDescription TalkBack-Text; Standard "Satz [setNumber]"/"Set [setNumber]".
 */
@Composable
fun SetCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    setNumber: String? = null,
    enabled: Boolean = true,
    contentDescription: String? = if (setNumber != null) {
        stringResource(R.string.designsystem_set_checkbox, setNumber)
    } else {
        null
    },
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        targetValue = if (checked) scheme.primary else scheme.surfaceContainerHigh,
        label = "setCheckboxContainer",
    )
    val toggleModifier = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { newValue ->
                    if (newValue) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onCheckedChange(newValue)
                },
            )
    } else {
        Modifier
    }
    val descriptionModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .then(toggleModifier)
            .then(descriptionModifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(Sizing.setCheckbox),
            shape = MaterialTheme.shapes.medium,
            color = container,
            contentColor = if (checked) scheme.onPrimary else scheme.onSurface,
            border = if (checked) null else BorderStroke(Sizing.setCheckboxBorder, scheme.outline),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (checked) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(Sizing.icon))
                } else if (setNumber != null) {
                    Text(
                        text = setNumber,
                        style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
            }
        }
    }
}
