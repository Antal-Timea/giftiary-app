package app.giftiary.presentation.giftideas

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.giftiary.R
import app.giftiary.domain.model.GiftIdea
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryDivider
import app.giftiary.presentation.components.GiftiaryPrimaryButton
import app.giftiary.presentation.components.GiftiaryTaskHeader
import app.giftiary.presentation.components.GiftiaryTaskStatus
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.OptionsMenu
import app.giftiary.presentation.components.PendingSyncStatus
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.gifts.DetailContextCard
import app.giftiary.presentation.gifts.DetailFactRow
import app.giftiary.presentation.gifts.DetailFactValue
import app.giftiary.presentation.gifts.DetailLinkValue
import app.giftiary.presentation.gifts.DetailNote
import app.giftiary.presentation.gifts.PhotoOrPlaceholder
import app.giftiary.presentation.gifts.giftPrice
import app.giftiary.presentation.occasions.occasionDayMonth
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.theme.GiftiaryType
import java.time.Instant
import java.util.UUID

@Composable
fun GiftIdeaDetailScreen(
    viewModel: GiftIdeaDetailViewModel,
    // Null beside the history list, which stays in view instead.
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
    onEditGiftIdea: (UUID) -> Unit,
    onMarkAsGiven: (UUID) -> Unit,
    onOpenPerson: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.content) {
        if (state.content == GiftIdeaDetailContent.Deleted) onDeleted()
    }
    GiftIdeaDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEditGiftIdea(it.idea.id) },
        onMarkAsGiven = { onMarkAsGiven(it.idea.id) },
        onOpenPerson = { onOpenPerson(it.id) },
        onStatusChange = viewModel::onStatusChange,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onWriteErrorShown = viewModel::onWriteErrorShown,
        onPhotoRetry = viewModel::onPhotoRetry,
    )
}

@Composable
fun GiftIdeaDetailScreen(
    state: GiftIdeaDetailUiState,
    onBack: (() -> Unit)?,
    onEdit: (GiftIdeaEntry) -> Unit,
    onMarkAsGiven: (GiftIdeaEntry) -> Unit,
    onOpenPerson: (Person) -> Unit,
    onStatusChange: (GiftIdeaStatus) -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onWriteErrorShown: () -> Unit,
    onPhotoRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val writeFailedMessage = stringResource(R.string.gift_idea_write_failed)
    LaunchedEffect(state.actions.writeFailed) {
        if (state.actions.writeFailed) {
            snackbarHostState.showSnackbar(writeFailedMessage)
            onWriteErrorShown()
        }
    }

    val entry = (state.content as? GiftIdeaDetailContent.Loaded)?.entry
    Scaffold(
        modifier = modifier,
        containerColor = GiftiaryTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            GiftIdeaDetailContent.Loading, GiftIdeaDetailContent.Deleted -> IdeaStatus(null, onBack, contentModifier)
            GiftIdeaDetailContent.NotFound -> IdeaStatus(stringResource(R.string.gift_idea_not_found), onBack, contentModifier)
            GiftIdeaDetailContent.LoadFailed -> IdeaStatus(stringResource(R.string.gift_idea_load_failed), onBack, contentModifier)
            is GiftIdeaDetailContent.Loaded -> GiftIdeaDetails(
                entry = content.entry,
                waitingToSync = state.waitingToSync,
                photo = state.photo,
                busy = state.actions.isBusy,
                onBack = onBack,
                onEdit = { onEdit(content.entry) },
                onMarkAsGiven = { onMarkAsGiven(content.entry) },
                onOpenPerson = onOpenPerson,
                onStatusChange = onStatusChange,
                onDeleteRequested = onDeleteRequested,
                onPhotoRetry = onPhotoRetry,
                modifier = contentModifier,
            )
        }
    }

    if (state.actions.confirmingDelete && entry != null) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.gift_idea_delete_dialog_title, entry.idea.name),
            message = stringResource(R.string.gift_idea_delete_dialog_message),
            confirmLabel = stringResource(R.string.gift_delete_confirm),
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteDismissed,
        )
    }
}

/**
 * The approved gift idea boards: who it is for and how far along it is on aqua, the photo if it has
 * one, the facts it has, the note, and one next step. Without a photo the page stays compact. The
 * step follows the status: an idea is planned, a planned one bought, a bought one given.
 */
@Composable
private fun GiftIdeaDetails(
    entry: GiftIdeaEntry,
    waitingToSync: Boolean,
    photo: GiftIdeaDetailPhoto?,
    busy: Boolean,
    onBack: (() -> Unit)?,
    onEdit: () -> Unit,
    onMarkAsGiven: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onStatusChange: (GiftIdeaStatus) -> Unit,
    onDeleteRequested: () -> Unit,
    onPhotoRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idea = entry.idea
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .giftiaryReadableWidth()
            .padding(start = GiftiarySpacing.taskGutter, end = GiftiarySpacing.taskGutter, top = GiftiarySpacing.browseGutter, bottom = GiftiarySpacing.xxl),
    ) {
        GiftiaryTaskHeader(
            title = idea.name,
            subtitle = null,
            onBack = onBack,
            eyebrow = stringResource(R.string.gift_idea_eyebrow).uppercase(),
            menu = {
                OptionsMenu(R.drawable.ic_more_vertical, stringResource(R.string.gift_idea_more, idea.name)) {
                    Option(stringResource(R.string.gift_edit), R.drawable.ic_edit, onEdit, enabled = !busy)
                    // Below, an idea's next step is planning it; giving it straight away stays possible here.
                    if (idea.status == GiftIdeaStatus.IDEA) {
                        Option(stringResource(R.string.gift_idea_mark_as_given), R.drawable.ic_gift_glyph, onMarkAsGiven, enabled = !busy)
                    }
                    Option(stringResource(R.string.gift_idea_delete), R.drawable.ic_delete, onDeleteRequested, enabled = !busy, quiet = true)
                }
            },
        )
        IdeaContext(entry = entry, onOpenPerson = onOpenPerson, modifier = Modifier.padding(top = GiftiarySpacing.l))
        if (waitingToSync) PendingSyncStatus(modifier = Modifier.padding(top = GiftiarySpacing.m))
        // Nothing while it is read, rather than "not available" for a moment.
        if (photo != null && !photo.loading) {
            Box(modifier = Modifier.padding(top = GiftiarySpacing.l)) {
                PhotoOrPlaceholder(photo.photo, stringResource(R.string.gift_idea_photo_description))
            }
            if (photo.photo == null) {
                TextButton(
                    onClick = onPhotoRetry,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
                ) {
                    Text(text = stringResource(R.string.gift_idea_photo_retry), style = GiftiaryType.actionSmall)
                }
            }
        }
        IdeaFacts(idea = idea, modifier = Modifier.padding(top = GiftiarySpacing.s))
        idea.note?.takeIf { it.isNotBlank() }?.let { DetailNote(it) }
        NextStep(
            status = idea.status,
            busy = busy,
            onStatusChange = onStatusChange,
            onMarkAsGiven = onMarkAsGiven,
            modifier = Modifier.padding(top = GiftiarySpacing.xxl),
        )
    }
}

/** Loading, gone or unreadable: the back circle stays, unless the history list is beside it. */
@Composable
private fun IdeaStatus(message: String?, onBack: (() -> Unit)?, modifier: Modifier) {
    when {
        onBack != null -> GiftiaryTaskStatus(message, onBack, modifier)
        message == null -> LoadingContent(modifier)
        else -> MessageContent(message, modifier)
    }
}

@Composable
private fun IdeaContext(entry: GiftIdeaEntry, onOpenPerson: (Person) -> Unit, modifier: Modifier = Modifier) {
    val recipient = entry.recipient
    val occasion = entry.occasion?.let { "${occasionTitle(it)} · ${occasionDayMonth(it)}" }
    DetailContextCard(
        name = recipient?.name.orEmpty(),
        title = recipient?.let { stringResource(R.string.gift_idea_for_person, it.name) } ?: occasion.orEmpty(),
        modifier = modifier,
        supporting = occasion.takeIf { recipient != null },
        onClickLabel = stringResource(R.string.people_open),
        onClick = recipient?.let { { onOpenPerson(it) } },
        trailing = { StatusLabel(entry.idea.status) },
    )
}

/** The status in words, with an icon beside it; never colour alone. */
@Composable
private fun StatusLabel(status: GiftIdeaStatus) {
    val colors = GiftiaryTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(status.icon), contentDescription = null, modifier = Modifier.size(16.dp), tint = colors.textPrimary)
        Text(text = giftIdeaStatusLabel(status), style = GiftiaryType.action, color = colors.textPrimary)
    }
}

@get:DrawableRes
private val GiftIdeaStatus.icon: Int
    get() = when (this) {
        GiftIdeaStatus.IDEA -> R.drawable.ic_idea
        GiftIdeaStatus.PLANNED -> R.drawable.ic_calendar
        GiftIdeaStatus.PURCHASED -> R.drawable.ic_check
    }

/**
 * What was paid once it is known, with the estimate beneath; otherwise the estimate. The link
 * follows. Facts the idea does not have are left out rather than shown empty.
 */
@Composable
private fun IdeaFacts(idea: GiftIdea, modifier: Modifier = Modifier) {
    val estimate = idea.estimatedPrice?.let { stringResource(R.string.wish_price_estimate, giftPrice(it)) }
    val rows = buildList<@Composable () -> Unit> {
        val paid = idea.actualPrice
        if (paid != null) {
            add {
                DetailFactRow(stringResource(R.string.gift_idea_price_paid)) {
                    DetailFactValue(giftPrice(paid), estimate?.let { stringResource(R.string.gift_idea_estimated_line, it) })
                }
            }
        } else if (estimate != null) {
            add { DetailFactRow(stringResource(R.string.gift_idea_estimated_price)) { DetailFactValue(estimate) } }
        }
        idea.productUrl?.let { url -> add { DetailFactRow(stringResource(R.string.gift_product_url)) { DetailLinkValue(url) } } }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, row ->
            if (index > 0) GiftiaryDivider()
            row()
        }
    }
}

@Composable
private fun NextStep(
    status: GiftIdeaStatus,
    busy: Boolean,
    onStatusChange: (GiftIdeaStatus) -> Unit,
    onMarkAsGiven: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.s),
    ) {
        val primary = Modifier.fillMaxWidth()
        when (status) {
            GiftIdeaStatus.IDEA -> {
                GiftiaryPrimaryButton(stringResource(R.string.gift_idea_plan), { onStatusChange(GiftIdeaStatus.PLANNED) }, primary, enabled = !busy, trailingArrow = true)
                QuietStep(R.drawable.ic_check, stringResource(R.string.gift_idea_mark_purchased), { onStatusChange(GiftIdeaStatus.PURCHASED) }, enabled = !busy)
            }
            GiftIdeaStatus.PLANNED -> {
                GiftiaryPrimaryButton(stringResource(R.string.gift_idea_mark_purchased), { onStatusChange(GiftIdeaStatus.PURCHASED) }, primary, enabled = !busy, trailingArrow = true)
                QuietStep(R.drawable.ic_gift_glyph, stringResource(R.string.gift_idea_mark_as_given), onMarkAsGiven, enabled = !busy)
            }
            GiftIdeaStatus.PURCHASED -> {
                GiftiaryPrimaryButton(stringResource(R.string.gift_idea_mark_as_given), onMarkAsGiven, primary, enabled = !busy, trailingArrow = true)
                Text(
                    text = stringResource(R.string.gift_idea_given_caption),
                    style = GiftiaryType.bodySmall,
                    color = GiftiaryTheme.colors.textMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The quieter second step under the primary one, teal and with its icon, as on the board. */
@Composable
private fun QuietStep(@DrawableRes icon: Int, text: String, onClick: () -> Unit, enabled: Boolean) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(GiftiarySpacing.s))
        Text(text = text, style = GiftiaryType.actionSmall)
    }
}

@PreviewLightDark
@Composable
private fun GiftIdeaDetailPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        GiftIdeaDetailScreen(
            state = GiftIdeaDetailUiState(
                content = GiftIdeaDetailContent.Loaded(
                    GiftIdeaEntry(
                        idea = GiftIdea(
                            id = UUID.randomUUID(),
                            recipientPersonId = anna.id,
                            occasionId = null,
                            name = "Wool scarf",
                            status = GiftIdeaStatus.PLANNED,
                            estimatedPrice = Money(10000, "HUF"),
                            actualPrice = null,
                            photoPath = null,
                            productUrl = "https://example.com/scarf",
                            note = "Something green.",
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                        recipient = anna,
                        occasion = null,
                    ),
                ),
            ),
            onBack = {},
            onEdit = {},
            onMarkAsGiven = {},
            onOpenPerson = {},
            onStatusChange = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onWriteErrorShown = {},
            onPhotoRetry = {},
        )
    }
}
