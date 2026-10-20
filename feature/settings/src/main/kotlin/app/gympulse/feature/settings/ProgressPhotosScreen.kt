package app.gympulse.feature.settings

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Face
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gympulse.core.designsystem.adaptive.currentWindowWidthClass
import app.gympulse.core.designsystem.component.EmptyState
import app.gympulse.core.designsystem.theme.ExpressiveShapes
import app.gympulse.core.designsystem.theme.Spacing
import app.gympulse.core.model.ProgressPhoto
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Galerie der Fortschrittsfotos mit Aufnahme, Detailansicht und Vorher/Nachher-Vergleich. */
@Composable
internal fun ProgressPhotosScreen(
    modifier: Modifier = Modifier,
    viewModel: ProgressPhotosViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var firstId by rememberSaveable { mutableStateOf<String?>(null) }
    var secondId by rememberSaveable { mutableStateOf<String?>(null) }
    var comparing by rememberSaveable { mutableStateOf(false) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var noteFor by rememberSaveable { mutableStateOf<String?>(null) }
    var failed by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ProgressPhoto?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val name = pendingCapture
        pendingCapture = null
        if (name != null) {
            if (success && viewModel.fileNamed(name).length() > 0) noteFor = name else viewModel.discard(name)
        }
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = viewModel.import(uri)
                if (name != null) noteFor = name else failed = true
            }
        }
    }

    fun startCapture() {
        val capture = viewModel.newCapture()
        pendingCapture = capture.fileName
        try {
            takePicture.launch(capture.uri)
        } catch (_: ActivityNotFoundException) {
            pendingCapture = null
            viewModel.discard(capture.fileName)
            failed = true
        }
    }

    val photos = state.photos
    val detail = photos.firstOrNull { it.id == detailId }
    val first = photos.firstOrNull { it.id == firstId }
    val second = photos.firstOrNull { it.id == secondId }

    BackHandler(enabled = comparing) { comparing = false }
    BackHandler(enabled = !comparing && selecting) {
        selecting = false
        firstId = null
        secondId = null
    }
    BackHandler(enabled = !comparing && !selecting && detail != null) { detailId = null }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        when {
            !state.enabled -> EmptyState(
                icon = Icons.Filled.Face,
                title = stringResource(R.string.photos_title),
                message = stringResource(R.string.photos_disabled),
            )
            comparing && first != null && second != null -> CompareView(
                first = first,
                second = second,
                fileOf = viewModel::fileOf,
                onClose = { comparing = false },
            )
            detail != null && !selecting -> DetailView(
                photo = detail,
                file = viewModel.fileOf(detail),
                onCompare = {
                    selecting = true
                    firstId = detail.id
                    secondId = null
                    detailId = null
                },
                onDelete = { deleting = detail },
                onClose = { detailId = null },
            )
            else -> GalleryGrid(
                photos = photos,
                selecting = selecting,
                selectedIds = listOfNotNull(firstId, secondId),
                fileOf = viewModel::fileOf,
                onTake = ::startCapture,
                onPick = {
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onStartCompare = {
                    selecting = true
                    firstId = null
                    secondId = null
                },
                onCancelCompare = {
                    selecting = false
                    firstId = null
                    secondId = null
                },
                onConfirmCompare = {
                    comparing = true
                    selecting = false
                },
                onPhotoClick = { photo ->
                    if (selecting) {
                        when (photo.id) {
                            firstId -> firstId = null
                            secondId -> secondId = null
                            else -> if (firstId == null) firstId = photo.id else secondId = photo.id
                        }
                    } else {
                        detailId = photo.id
                    }
                },
            )
        }
    }

    noteFor?.let { name ->
        NoteDialog(
            onSave = { note ->
                viewModel.save(name, note)
                noteFor = null
            },
            onDiscard = {
                viewModel.discard(name)
                noteFor = null
            },
        )
    }
    deleting?.let { photo ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.photos_delete_title)) },
            text = { Text(stringResource(R.string.photos_delete_message, formatPhotoDate(photo))) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(photo)
                        deleting = null
                        detailId = null
                    },
                ) { Text(stringResource(R.string.photos_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.settings_action_cancel)) }
            },
        )
    }
    if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            title = { Text(stringResource(R.string.photos_error_title)) },
            text = { Text(stringResource(R.string.photos_error_message)) },
            confirmButton = {
                TextButton(onClick = { failed = false }) { Text(stringResource(R.string.photos_error_ok)) }
            },
        )
    }
}

@Composable
private fun GalleryGrid(
    photos: List<ProgressPhoto>,
    selecting: Boolean,
    selectedIds: List<String>,
    fileOf: (ProgressPhoto) -> java.io.File,
    onTake: () -> Unit,
    onPick: () -> Unit,
    onStartCompare: () -> Unit,
    onCancelCompare: () -> Unit,
    onConfirmCompare: () -> Unit,
    onPhotoClick: (ProgressPhoto) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = Spacing.xxxl * 2),
        modifier = Modifier
            .widthIn(max = Spacing.maxContentWidth)
            .fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = currentWindowWidthClass().horizontalMargin,
            vertical = Spacing.m,
        ),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (selecting) {
                    Text(
                        text = stringResource(R.string.photos_select_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Button(onClick = onConfirmCompare, enabled = selectedIds.size == 2) {
                            Text(stringResource(R.string.photos_compare_show))
                        }
                        TextButton(onClick = onCancelCompare) {
                            Text(stringResource(R.string.settings_action_cancel))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Button(onClick = onTake) { Text(stringResource(R.string.photos_take)) }
                        OutlinedButton(onClick = onPick) { Text(stringResource(R.string.photos_pick)) }
                    }
                    if (photos.size >= 2) {
                        TextButton(onClick = onStartCompare) { Text(stringResource(R.string.photos_compare)) }
                    }
                }
            }
        }
        if (photos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    icon = Icons.Filled.Face,
                    title = stringResource(R.string.photos_title),
                    message = stringResource(R.string.photos_empty),
                )
            }
        } else {
            items(photos, key = { it.id }) { photo ->
                PhotoCell(
                    photo = photo,
                    file = fileOf(photo),
                    selecting = selecting,
                    selected = photo.id in selectedIds,
                    onClick = { onPhotoClick(photo) },
                )
            }
        }
    }
}

@Composable
private fun PhotoCell(
    photo: ProgressPhoto,
    file: java.io.File,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val description = stringResource(R.string.photos_item_description, formatPhotoDate(photo))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PORTRAIT_RATIO)
            .clip(shape)
            .then(if (selected) Modifier.border(Spacing.xxs, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = file,
                contentDescription = description,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (selecting && selected) {
            Surface(
                shape = ExpressiveShapes.full,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.xs)
                    .size(Spacing.l),
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(Spacing.xxs))
            }
        }
    }
}

@Composable
private fun DetailView(
    photo: ProgressPhoto,
    file: java.io.File,
    onCompare: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .widthIn(max = Spacing.maxFormWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = currentWindowWidthClass().horizontalMargin, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.photos_item_description, formatPhotoDate(photo)),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
        )
        Text(text = formatPhotoDate(photo), style = MaterialTheme.typography.titleMedium)
        Text(
            text = photo.note ?: stringResource(R.string.photos_no_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Button(onClick = onCompare) { Text(stringResource(R.string.photos_compare_with)) }
            OutlinedButton(onClick = onDelete) { Text(stringResource(R.string.photos_delete)) }
        }
        TextButton(onClick = onClose) { Text(stringResource(R.string.photos_back)) }
    }
}

/** Zwei Fotos nebeneinander; das ältere steht links als "Before". */
@Composable
private fun CompareView(
    first: ProgressPhoto,
    second: ProgressPhoto,
    fileOf: (ProgressPhoto) -> java.io.File,
    onClose: () -> Unit,
) {
    val (before, after) = if (first.takenAt <= second.takenAt) first to second else second to first
    val days = Duration.between(before.takenAt, after.takenAt).toDays().toInt()
    Column(
        modifier = Modifier
            .widthIn(max = Spacing.maxContentWidth)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = currentWindowWidthClass().horizontalMargin, vertical = Spacing.m),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            CompareColumn(R.string.photos_before, before, fileOf(before), Modifier.weight(1f))
            CompareColumn(R.string.photos_after, after, fileOf(after), Modifier.weight(1f))
        }
        Text(
            text = pluralStringResource(R.plurals.photos_days_apart, days, days),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onClose) { Text(stringResource(R.string.photos_back)) }
    }
}

@Composable
private fun CompareColumn(
    labelRes: Int,
    photo: ProgressPhoto,
    file: java.io.File,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.labelLarge)
        AsyncImage(
            model = file,
            contentDescription = stringResource(R.string.photos_item_description, formatPhotoDate(photo)),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(PORTRAIT_RATIO)
                .clip(MaterialTheme.shapes.medium),
        )
        Text(
            text = formatPhotoDate(photo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        photo.note?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun NoteDialog(onSave: (String) -> Unit, onDiscard: () -> Unit) {
    var note by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDiscard,
        title = { Text(stringResource(R.string.photos_note_title)) },
        text = {
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.photos_note_label)) },
                maxLines = NOTE_MAX_LINES,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(note) }) { Text(stringResource(R.string.settings_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.photos_discard)) }
        },
    )
}

@Composable
private fun formatPhotoDate(photo: ProgressPhoto): String {
    val formatter = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    return formatter.format(photo.takenAt.atZone(ZoneId.systemDefault()))
}

/** Hochformat 3:4, typisch für Körperfotos. */
private const val PORTRAIT_RATIO = 3f / 4f
private const val NOTE_MAX_LINES = 3
