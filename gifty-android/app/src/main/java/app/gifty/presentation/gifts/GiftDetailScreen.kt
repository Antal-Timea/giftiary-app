package app.gifty.presentation.gifts

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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.gifty.R
import app.gifty.domain.model.Gift
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.Money
import app.gifty.domain.model.Person
import app.gifty.domain.repository.GiftEntry
import app.gifty.presentation.components.GiftyConfirmDialog
import app.gifty.presentation.components.GiftyTopBar
import app.gifty.presentation.components.LoadingContent
import app.gifty.presentation.components.MessageContent
import app.gifty.presentation.occasions.occasionTitle
import app.gifty.presentation.theme.GiftySpacing
import app.gifty.presentation.theme.GiftyTheme
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Composable
fun GiftDetailScreen(
    viewModel: GiftDetailViewModel,
    onBack: () -> Unit,
    onEditGift: (UUID) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.content) {
        if (state.content == GiftDetailContent.Deleted) onBack()
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
    onBack: () -> Unit,
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
            GiftyTopBar(
                title = entry?.gift?.name.orEmpty(),
                onBack = onBack,
                actions = {
                    if (entry != null) {
                        TextButton(
                            onClick = { onEditGift(entry) },
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
        GiftyConfirmDialog(
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
            .padding(horizontal = GiftySpacing.l, vertical = GiftySpacing.s),
        verticalArrangement = Arrangement.spacedBy(GiftySpacing.l),
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
            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.textSecondary),
        ) {
            Icon(painter = painterResource(R.drawable.ic_delete), contentDescription = null)
            Spacer(Modifier.width(GiftySpacing.s))
            Text(stringResource(R.string.gift_delete))
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs)) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = GiftyTheme.colors.textSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, color = GiftyTheme.colors.textPrimary)
    }
}

@Composable
private fun ProductLink(url: String) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(GiftySpacing.xs)) {
        Text(
            text = stringResource(R.string.gift_product_url),
            style = MaterialTheme.typography.labelMedium,
            color = GiftyTheme.colors.textSecondary,
        )
        TextButton(
            // A stored link may not be openable (no browser, unusual scheme); that is not an app error.
            onClick = { runCatching { uriHandler.openUri(url) } },
            colors = ButtonDefaults.textButtonColors(contentColor = GiftyTheme.colors.primaryActionText),
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun GiftDetailPreview() {
    val anna = Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH)
    GiftyTheme {
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
