package app.gympulse.feature.plans

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PlanEditorScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var showDiscardDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val saveFailedMessage = stringResource(R.string.plans_save_failed)

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onClose()
    }
    LaunchedEffect(state.saveFailed) {
        if (state.saveFailed) {
            snackbarHostState.showSnackbar(saveFailedMessage)
            viewModel.onSaveFailureShown()
        }
    }

    val picker = state.picker
    // Zuerst schließt Zurück die Übungsauswahl, danach fragt es bei ungespeicherten Änderungen nach.
    BackHandler(enabled = picker != null) { viewModel.closePicker() }
    BackHandler(enabled = picker == null && state.isDirty && !state.isSaved) { showDiscardDialog = true }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (picker == null && !state.isLoading) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.plans_save)) },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    onClick = viewModel::save,
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            picker != null -> ExercisePicker(
                exercises = exercises,
                state = picker,
                onQueryChange = viewModel::onPickerQueryChange,
                onToggle = viewModel::onPickerToggle,
                onConfirm = viewModel::confirmPicker,
                onClose = viewModel::closePicker,
                modifier = Modifier.padding(padding),
            )

            else -> EditorContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.plans_discard_title)) },
            text = { Text(stringResource(R.string.plans_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onClose()
                    },
                ) { Text(stringResource(R.string.plans_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.plans_discard_keep))
                }
            },
        )
    }
}

