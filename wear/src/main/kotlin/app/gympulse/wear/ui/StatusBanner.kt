package app.gympulse.wear.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.gympulse.core.wearprotocol.AckResult
import app.gympulse.core.wearprotocol.CommandAck
import app.gympulse.wear.R
import app.gympulse.wear.data.WearConnection
import app.gympulse.wear.theme.WearSpacing

/** Eine Zeile Status: Offline-Hinweis, Protokoll-Warnung oder ein vom Telefon ignoriertes Kommando. */
sealed interface StatusMessage {
    data object Offline : StatusMessage
    data object UpdateRequired : StatusMessage
    data class Notice(val result: AckResult) : StatusMessage
}

/** Wichtigste Meldung zuerst: Protokollkonflikt, dann Hinweise des Telefons, dann Offline. */
fun statusMessage(connection: WearConnection, incompatibleProtocol: Boolean, notices: List<CommandAck>): StatusMessage? =
    when {
        incompatibleProtocol -> StatusMessage.UpdateRequired
        notices.isNotEmpty() -> StatusMessage.Notice(notices.first().result)
        connection == WearConnection.DISCONNECTED -> StatusMessage.Offline
        else -> null
    }

@Composable
fun StatusBanner(
    message: StatusMessage,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = when (message) {
        StatusMessage.Offline -> stringResource(R.string.status_offline_syncing)
        StatusMessage.UpdateRequired -> stringResource(R.string.status_update_required)
        is StatusMessage.Notice -> when (message.result) {
            AckResult.SESSION_ENDED -> stringResource(R.string.notice_session_ended)
            AckResult.SESSION_MISMATCH -> stringResource(R.string.notice_session_mismatch)
            else -> stringResource(R.string.notice_invalid)
        }
    }
    val isError = message !is StatusMessage.Offline
    val dismissLabel = stringResource(R.string.notice_dismiss_cd)
    val clickable = if (message is StatusMessage.Notice) {
        Modifier
            .heightIn(min = WearSpacing.minTouchTarget)
            .clickable(role = Role.Button, onClickLabel = dismissLabel, onClick = onDismissNotice)
            .semantics { contentDescription = "$text. $dismissLabel" }
    } else {
        Modifier
    }
    Box(modifier = modifier.fillMaxWidth().then(clickable), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
