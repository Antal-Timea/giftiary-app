package app.gifty.presentation.giftideas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.GiftIdea
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.Money
import app.gifty.domain.model.Person
import app.gifty.domain.repository.GiftIdeaEntry
import app.gifty.presentation.components.GiftyConfirmDialog
import app.gifty.presentation.components.GiftyPrimaryButton
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.gifts.DetailField
import app.gifty.presentation.gifts.ProductLink
import app.gifty.presentation.gifts.giftPrice
import app.gifty.presentation.occasions.occasionTitle
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
import java.time.Instant
import java.util.UUID

@Composable
fun GiftIdeaDetailScreen(
    viewModel: GiftIdeaDetailViewModel,
    onBack: () -> Unit,
    onEditGiftIdea: (UUID) -> Unit,
    onMarkAsGiven: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.content) {
        if (state.content == GiftIdeaDetailContent.Deleted) onBack()
    }
    GiftIdeaDetailScreen(
        state = state,
        onBack = onBack,
        onEdit = { onEditGiftIdea(it.idea.id) },
        onMarkAsGiven = { onMarkAsGiven(it.idea.id) },
        onStatusChange = viewModel::onStatusChange,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onWriteErrorShown = viewModel::onWriteErrorShown,
    )
}

@Composable
fun GiftIdeaDetailScreen(
    state: GiftIdeaDetailUiState,
    onBack: () -> Unit,
    onEdit: (GiftIdeaEntry) -> Unit,
    onMarkAsGiven: (GiftIdeaEntry) -> Unit,
    onStatusChange: (GiftIdeaStatus) -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onWriteErrorShown: () -> Unit,
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
        topBar = {
            GiftyTopBar(
                title = entry?.idea?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (entry != null) {
                        TextButton(
                            onClick = { onEdit(entry) },
                            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.textPrimary),
                        ) {
                            Text(stringResource(R.string.gift_edit))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val contentModifier = Modifier.padding(contentPadding)
        when (val content = state.content) {
            GiftIdeaDetailContent.Loading, GiftIdeaDetailContent.Deleted -> LoadingContent(contentModifier)
            GiftIdeaDetailContent.NotFound -> MessageContent(stringResource(R.string.gift_idea_not_found), contentModifier)
            GiftIdeaDetailContent.LoadFailed -> MessageContent(stringResource(R.string.gift_idea_load_failed), contentModifier)
            is GiftIdeaDetailContent.Loaded -> GiftIdeaDetails(
                entry = content.entry,
                busy = state.actions.isBusy,
                onStatusChange = onStatusChange,
                onMarkAsGiven = { onMarkAsGiven(content.entry) },
                onDeleteRequested = onDeleteRequested,
                modifier = contentModifier,
            )
        }
    }

    if (state.actions.confirmingDelete && entry != null) {
        GiftyConfirmDialog(
            title = stringResource(R.string.gift_idea_delete_dialog_title, entry.idea.name),
            message = stringResource(R.string.gift_idea_delete_dialog_message),
            confirmLabel = stringResource(R.string.gift_delete_confirm),
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteDismissed,
        )
    }
}

@Composable
private fun GiftIdeaDetails(
    entry: GiftIdeaEntry,
    busy: Boolean,
    onStatusChange: (GiftIdeaStatus) -> Unit,
    onMarkAsGiven: () -> Unit,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idea = entry.idea
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.l),
    ) {
        GiftIdeaStatusSelector(selected = idea.status, onSelect = onStatusChange, enabled = !busy)
        entry.recipient?.let { DetailField(label = stringResource(R.string.gift_recipient), value = it.name) }
        entry.occasion?.let { DetailField(label = stringResource(R.string.gift_occasion), value = occasionTitle(it)) }
        idea.estimatedPrice?.let {
            DetailField(label = stringResource(R.string.gift_idea_estimated_price), value = giftPrice(it))
        }
        idea.actualPrice?.let { DetailField(label = stringResource(R.string.gift_idea_actual_price), value = giftPrice(it)) }
        idea.productUrl?.let { ProductLink(it) }
        idea.note?.let { DetailField(label = stringResource(R.string.gift_note), value = it) }

        GiftyPrimaryButton(
            text = stringResource(R.string.gift_idea_mark_as_given),
            onClick = onMarkAsGiven,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = GiftySpacing.s),
            enabled = !busy,
        )
        // Deliberately quiet: deleting should not compete with planning.
        TextButton(
            onClick = onDeleteRequested,
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy,
            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.textSecondary),
        ) {
            Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null)
            Spacer(Modifier.width(GiftySpacing.s))
            Text(stringResource(R.string.gift_idea_delete))
        }
    }
}

@PreviewLightDark
@Composable
private fun GiftIdeaDetailPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftyTheme {
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
            onStatusChange = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onWriteErrorShown = {},
        )
    }
}
