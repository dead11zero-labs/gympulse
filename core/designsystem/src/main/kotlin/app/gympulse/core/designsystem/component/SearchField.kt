package app.gympulse.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Sizing

/**
 * Suchfeld als tonale Pille (`surfaceContainerHigh`, voll gerundet, ohne Rahmen) mit
 * Such-Icon und Löschen-Button, sobald Text eingegeben ist.
 *
 * @param placeholder Hinweistext, solange das Feld leer ist.
 * @param clearContentDescription Beschreibung des Löschen-Buttons für TalkBack.
 */
@Composable
fun GymPulseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val trailing: (@Composable () -> Unit)? = if (value.isNotEmpty()) {
        {
            IconButton(onClick = { onValueChange("") }) {
                Icon(Icons.Filled.Close, contentDescription = clearContentDescription)
            }
        }
    } else {
        null
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().heightIn(min = Sizing.minTouchTarget),
        singleLine = true,
        shape = ExpressiveShapes.full,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = scheme.surfaceContainerHigh,
            unfocusedContainerColor = scheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
        ),
    )
}
