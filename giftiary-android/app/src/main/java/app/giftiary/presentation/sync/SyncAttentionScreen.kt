package app.giftiary.presentation.sync

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.AttentionItem
import app.giftiary.domain.model.AttentionKind
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.SyncItem
import app.giftiary.domain.model.SyncItemType
import app.giftiary.domain.model.SyncStatus
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.occasions.occasionTypeLabel
import app.giftiary.presentation.theme.GiftiaryShapes
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
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

/**
 * What this device could not settle with the account, as a utility page: the task header, then one
 * uncontained row per item, with Open and Got it (which closes the notice) only where the item allows
 * them. Loading and an unreadable status are the DS-05 task states; nothing claims the device matches
 * the account.
 */
@Composable
fun SyncAttentionScreen(
    content: SyncStatusContent,
    onBack: () -> Unit,
    onOpen: (SyncItem) -> Unit,
    onDismiss: (AttentionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier, containerColor = GiftiaryTheme.colors.background) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (content) {
            SyncStatusContent.Loading -> GiftiaryTaskStatus(null, onBack, contentModifier)
            SyncStatusContent.Unavailable -> GiftiaryTaskStatus(stringResource(R.string.sync_status_unavailable), onBack, contentModifier)
            is SyncStatusContent.Loaded -> LazyColumn(
                modifier = contentModifier
                    .fillMaxSize()
                    .giftiaryReadableWidth(),
                contentPadding = PaddingValues(
                    start = GiftiarySpacing.taskGutter,
                    end = GiftiarySpacing.taskGutter,
                    top = GiftiarySpacing.browseGutter,
                    bottom = GiftiarySpacing.xxl,
                ),
            ) {
                item {
                    GiftiaryTaskHeader(title = stringResource(R.string.sync_attention_title), subtitle = null, onBack = onBack, eyebrow = null)
                }
                val attention = content.status.attention
                if (attention.isEmpty()) {
                    item { NothingNeedsAttention(Modifier.padding(top = GiftiarySpacing.xxl)) }
                } else {
                    item { Spacer(Modifier.height(GiftiarySpacing.m)) }
                    itemsIndexed(attention, key = { _, item -> item.id }) { index, item ->
                        if (index > 0) GiftiaryDivider()
                        AttentionRow(item = item, onOpen = { onOpen(item.item) }, onDismiss = { onDismiss(item) })
                    }
                }
            }
        }
    }
}

/** The DS-05 empty state; it says how saving works rather than that everything is synced. */
@Composable
private fun NothingNeedsAttention(modifier: Modifier = Modifier) {
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(colors.thumbnailFallback, GiftiaryShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(52.dp), tint = colors.textLabel)
        }
        Text(
            text = stringResource(R.string.sync_attention_empty_title),
            modifier = Modifier
                .padding(top = GiftiarySpacing.xl)
                .semantics { heading() },
            style = GiftiaryType.sheetTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(R.string.sync_attention_empty_message),
            modifier = Modifier.padding(top = GiftiarySpacing.s),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
    }
}

@Composable
private fun AttentionRow(item: AttentionItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val colors = GiftiaryTheme.colors
    val name = syncItemName(item.item)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GiftiarySpacing.l),
    ) {
        Text(text = syncItemTypeLabel(item.item.type), style = GiftiaryType.label, color = colors.textLabel)
        Text(
            text = name,
            modifier = Modifier.padding(top = GiftiarySpacing.xs),
            style = GiftiaryType.rowTitle,
            color = colors.textPrimary,
        )
        Text(
            text = stringResource(attentionMessage(item.kind)),
            modifier = Modifier.padding(top = GiftiarySpacing.xs),
            style = GiftiaryType.body,
            color = colors.textSecondary,
        )
        if (item.canOpen || item.canDismiss) {
            FlowRow(
                modifier = Modifier.padding(top = GiftiarySpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
            ) {
                if (item.canOpen) {
                    AttentionAction(
                        text = stringResource(R.string.sync_attention_open),
                        clickLabel = stringResource(R.string.sync_attention_open_label, name),
                        onClick = onOpen,
                    )
                }
                if (item.canDismiss) {
                    AttentionAction(
                        text = stringResource(R.string.sync_attention_dismiss),
                        clickLabel = stringResource(R.string.sync_attention_dismiss_label, name),
                        onClick = onDismiss,
                        quiet = true,
                    )
                }
            }
        }
    }
}

/**
 * A row's text action, underlined like Allow in Settings, and 48dp tall. A list holds several, so
 * each click is announced with the item it is about.
 */
@Composable
private fun AttentionAction(text: String, clickLabel: String, onClick: () -> Unit, quiet: Boolean = false) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .padding(horizontal = GiftiarySpacing.xs),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = GiftiaryType.actionSmall.copy(textDecoration = TextDecoration.Underline),
            color = if (quiet) GiftiaryTheme.colors.textSecondary else GiftiaryTheme.colors.textPrimary,
        )
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
    val colors = GiftiaryTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.xs)) {
        val attention = status.attention.size
        if (attention > 0) {
            val label = stringResource(R.string.sync_status_open_attention)
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = label, role = Role.Button, onClick = onOpenAttention),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = pluralStringResource(R.plurals.sync_status_attention, attention, attention),
                    style = GiftiaryType.actionSmall,
                    color = colors.primaryActionText,
                )
            }
        }
        val waiting = status.waitingCount
        Text(
            text = if (waiting > 0) {
                pluralStringResource(R.plurals.sync_status_waiting, waiting, waiting)
            } else {
                stringResource(R.string.sync_status_nothing_waiting)
            },
            style = GiftiaryType.bodySmall,
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
    GiftiaryTheme {
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
