package app.gifty.presentation.sync

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.AttentionItem
import app.gifty.domain.model.AttentionKind
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.SyncItem
import app.gifty.domain.model.SyncItemType
import app.gifty.domain.model.SyncStatus
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.occasions.occasionTypeLabel
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
import java.util.UUID

@Composable
fun SyncAttentionScreen(
    onBack: () -> Unit,
    onOpen: (SyncItem) -> Unit,
    viewModel: SyncStatusViewModel = hiltViewModel(),
) {
    val content by viewModel.content.collectAsStateWithLifecycle()
    SyncAttentionScreen(content = content, onBack = onBack, onOpen = onOpen, onDismiss = viewModel::onDismiss)
}

@Composable
fun SyncAttentionScreen(
    content: SyncStatusContent,
    onBack: () -> Unit,
    onOpen: (SyncItem) -> Unit,
    onDismiss: (AttentionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { GiftyTopBar(title = stringResource(R.string.sync_attention_title), onBack = onBack) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (content) {
            SyncStatusContent.Loading -> LoadingContent(contentModifier)
            SyncStatusContent.Unavailable -> MessageContent(stringResource(R.string.sync_status_unavailable), contentModifier)
            is SyncStatusContent.Loaded -> if (content.status.attention.isEmpty()) {
                MessageContent(
                    title = stringResource(R.string.sync_attention_empty_title),
                    message = stringResource(R.string.sync_attention_empty_message),
                    modifier = contentModifier,
                )
            } else {
                LazyColumn(
                    modifier = contentModifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = GiftySpacing.s),
                ) {
                    items(content.status.attention, key = { it.id }) { item ->
                        AttentionRow(item = item, onOpen = { onOpen(item.item) }, onDismiss = { onDismiss(item) })
                        HorizontalDivider(color = GiftyTheme.colors.divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttentionRow(item: AttentionItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = GiftyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.m),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs),
    ) {
        Text(
            text = syncItemTypeLabel(item.item.type),
            style = MaterialTheme.typography.labelMedium,
            color = colors.textLabel,
        )
        Text(
            text = syncItemName(item.item),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(attentionMessage(item.kind)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        if (item.canOpen || item.canDismiss) {
            Row(horizontalArrangement = Arrangement.spacedBy(GiftySpacing.s)) {
                if (item.canOpen) {
                    TextButton(
                        onClick = onOpen,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.primaryActionText),
                    ) {
                        Text(stringResource(R.string.sync_attention_open), style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (item.canDismiss) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(contentColor = colors.textSecondary),
                    ) {
                        Text(stringResource(R.string.sync_attention_dismiss), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/**
 * The local sync status on Home: what needs attention, what is waiting. It never claims the
 * device matches the account, only what this device still has to send. Shows nothing until the
 * status has been read.
 */
@Composable
fun SyncStatusLine(content: SyncStatusContent, onOpenAttention: () -> Unit, modifier: Modifier = Modifier) {
    val status = (content as? SyncStatusContent.Loaded)?.status ?: return
    val colors = GiftyTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs)) {
        val attention = status.attention.size
        if (attention > 0) {
            val label = stringResource(R.string.sync_status_open_attention)
            Text(
                text = pluralStringResource(R.plurals.sync_status_attention, attention, attention),
                modifier = Modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onOpenAttention),
                style = MaterialTheme.typography.labelLarge,
                color = colors.primaryActionText,
            )
        }
        val waiting = status.waitingCount
        Text(
            text = if (waiting > 0) {
                pluralStringResource(R.plurals.sync_status_waiting, waiting, waiting)
            } else {
                stringResource(R.string.sync_status_nothing_waiting)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
    }
}

private fun attentionMessage(kind: AttentionKind): Int = when (kind) {
    AttentionKind.REPLACED_BY_OTHER_DEVICE -> R.string.sync_attention_replaced
    AttentionKind.DUPLICATE_GIFT_REMOVED -> R.string.sync_attention_duplicate_gift
    AttentionKind.REFUSED -> R.string.sync_attention_refused
    AttentionKind.UNAVAILABLE -> R.string.sync_attention_unavailable
}

@Composable
@ReadOnlyComposable
private fun syncItemTypeLabel(type: SyncItemType): String = stringResource(
    when (type) {
        SyncItemType.PERSON -> R.string.sync_item_person
        SyncItemType.OCCASION -> R.string.sync_item_occasion
        SyncItemType.GIFT_IDEA -> R.string.sync_item_gift_idea
        SyncItemType.GIFT -> R.string.sync_item_gift
        SyncItemType.WISHLIST -> R.string.sync_item_wishlist
        SyncItemType.WISH -> R.string.sync_item_wish
    },
)

/** As the item's own screens name it: a custom occasion by its title, any other by its type. */
@Composable
@ReadOnlyComposable
private fun syncItemName(item: SyncItem): String {
    val occasionType = item.occasionType
    val name = if (item.type == SyncItemType.OCCASION && occasionType != null) {
        item.name?.takeIf { occasionType == OccasionType.CUSTOM } ?: occasionTypeLabel(occasionType)
    } else {
        item.name
    }
    return name ?: stringResource(R.string.sync_item_unnamed)
}

@PreviewLightDark
@Composable
private fun SyncAttentionScreenPreview() {
    GiftyTheme {
        SyncAttentionScreen(
            content = SyncStatusContent.Loaded(
                SyncStatus(
                    waitingCount = 2,
                    attention = listOf(
                        AttentionItem(
                            UUID.randomUUID(), AttentionKind.REPLACED_BY_OTHER_DEVICE,
                            SyncItem(SyncItemType.PERSON, UUID.randomUUID(), "Anna"), canOpen = true,
                        ),
                        AttentionItem(
                            UUID.randomUUID(), AttentionKind.DUPLICATE_GIFT_REMOVED,
                            SyncItem(SyncItemType.GIFT, UUID.randomUUID(), "Scarf"), canOpen = false,
                        ),
                        AttentionItem(
                            UUID.randomUUID(), AttentionKind.REFUSED,
                            SyncItem(SyncItemType.OCCASION, UUID.randomUUID(), null, OccasionType.BIRTHDAY), canOpen = true,
                        ),
                    ),
                ),
            ),
            onBack = {},
            onOpen = {},
            onDismiss = {},
        )
    }
}
