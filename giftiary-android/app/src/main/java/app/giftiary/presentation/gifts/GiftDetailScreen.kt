package app.giftiary.presentation.gifts

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
import androidx.compose.material3.MaterialTheme
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
import app.giftiary.R
import app.giftiary.domain.model.Gift
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.presentation.components.GiftiaryConfirmDialog
import app.giftiary.presentation.components.GiftiaryTopBar
import app.giftiary.presentation.components.LoadingContent
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.giftiaryReadableWidth
import app.giftiary.presentation.occasions.occasionTitle
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun GiftDetailScreen(
    viewModel: GiftDetailViewModel,
    // Null beside the history list, which stays in view instead.
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
    onEditGift: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.content) {
        if (state.content == GiftDetailContent.Deleted) onDeleted()
    }
    GiftDetailScreen(
        state = state,
        onBack = onBack,
        onEditGift = { onEditGift(it.gift.id) },
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDeleteDismissed = viewModel::onDeleteDismissed,
        onDeleteErrorShown = viewModel::onDeleteErrorShown,
    )
}

@Composable
fun GiftDetailScreen(
    state: GiftDetailUiState,
    onBack: (() -> Unit)?,
    onEditGift: (GiftEntry) -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val deleteFailedMessage = stringResource(R.string.gift_delete_failed)
    LaunchedEffect(state.actions.deleteFailed) {
        if (state.actions.deleteFailed) {
            snackbarHostState.showSnackbar(deleteFailedMessage)
            onDeleteErrorShown()
        }
    }

    val entry = (state.content as? GiftDetailContent.Loaded)?.entry
    Scaffold(
        modifier = modifier,
        topBar = {
            GiftiaryTopBar(
                title = entry?.gift?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (entry != null) {
                        TextButton(
                            onClick = { onEditGift(entry) },
                            colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textPrimary),
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
        when (state.content) {
            GiftDetailContent.Loading, GiftDetailContent.Deleted -> LoadingContent(contentModifier)
            GiftDetailContent.NotFound -> MessageContent(stringResource(R.string.gift_not_found), contentModifier)
            GiftDetailContent.LoadFailed -> MessageContent(stringResource(R.string.gift_load_failed), contentModifier)
            is GiftDetailContent.Loaded -> GiftDetails(
                entry = state.content.entry,
                deleteEnabled = !state.actions.isDeleting,
                onDeleteRequested = onDeleteRequested,
                modifier = contentModifier,
            )
        }
    }

    if (state.actions.confirmingDelete && entry != null) {
        GiftiaryConfirmDialog(
            title = stringResource(R.string.gift_delete_dialog_title, entry.gift.name),
            message = stringResource(R.string.gift_delete_dialog_message),
            confirmLabel = stringResource(R.string.gift_delete_confirm),
            onConfirm = onDeleteConfirmed,
            onDismiss = onDeleteDismissed,
        )
    }
}

@Composable
private fun GiftDetails(
    entry: GiftEntry,
    deleteEnabled: Boolean,
    onDeleteRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gift = entry.gift
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .giftiaryReadableWidth()
            .padding(horizontal = GiftiarySpacing.l, vertical = GiftiarySpacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftiarySpacing.l),
    ) {
        GiftDirectionTag(gift.direction)
        giftPeople(entry)?.let { DetailField(label = giftPersonLabel(gift.direction), value = it) }
        DetailField(label = stringResource(R.string.gift_date), value = giftDate(gift.givenAt))
        entry.occasion?.let { DetailField(label = stringResource(R.string.gift_occasion), value = occasionTitle(it)) }
        gift.price?.let { DetailField(label = stringResource(R.string.gift_price), value = giftPrice(it)) }
        gift.productUrl?.let { ProductLink(it) }
        gift.note?.let { DetailField(label = stringResource(R.string.gift_note), value = it) }

        // Deliberately quiet: deleting should not compete with reading the gift.
        TextButton(
            onClick = onDeleteRequested,
            modifier = Modifier.fillMaxWidth(),
            enabled = deleteEnabled,
            colors = ButtonDefaults.textButtonColors(contentColor = GiftiaryTheme.colors.textSecondary),
        ) {
            Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null)
            Spacer(Modifier.width(GiftiarySpacing.s))
            Text(stringResource(R.string.gift_delete))
        }
    }
}

@PreviewLightDark
@Composable
private fun GiftDetailPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftiaryTheme {
        GiftDetailScreen(
            state = GiftDetailUiState(
                content = GiftDetailContent.Loaded(
                    GiftEntry(
                        gift = Gift(
                            id = UUID.randomUUID(),
                            direction = GiftDirection.GIVEN,
                            giverPersonId = null,
                            recipientPersonId = anna.id,
                            occasionId = null,
                            sourceGiftIdeaId = null,
                            name = "Birthday book",
                            givenAt = LocalDate.of(2026, 3, 12),
                            price = Money(12500, "HUF"),
                            photoPath = null,
                            productUrl = "https://example.com/book",
                            note = "Signed by the author.",
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                        giver = null,
                        recipient = anna,
                        occasion = null,
                    ),
                ),
            ),
            onBack = {},
            onEditGift = {},
            onDeleteRequested = {},
            onDeleteConfirmed = {},
            onDeleteDismissed = {},
            onDeleteErrorShown = {},
        )
    }
}
