package app.giftiary.presentation.gifts

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.giftiary.R
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.presentation.components.GiftiaryAdaptivePreviews
import app.giftiary.presentation.components.ListDetailPanes
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.theme.GiftiaryTheme
import java.util.UUID

/**
 * The gift history at any width. A wide window keeps the list in view and shows the selected gift
 * or idea beside it; narrower windows open it as a screen of its own, as on a phone.
 */
@Composable
internal fun AdaptiveGiftHistoryScreen(
    state: GiftHistoryUiState,
    onBack: (() -> Unit)?,
    onFilterChange: (GiftFilter) -> Unit,
    onAddGift: () -> Unit,
    onOpenGift: (UUID) -> Unit,
    onAddGiftIdea: () -> Unit,
    onOpenGiftIdea: (UUID) -> Unit,
    onSelectGift: (GiftEntry) -> Unit,
    onSelectIdea: (GiftIdeaEntry) -> Unit,
    onClearSelection: () -> Unit,
    giftDetail: @Composable (id: UUID, onDeleted: () -> Unit) -> Unit,
    ideaDetail: @Composable (id: UUID, onDeleted: () -> Unit) -> Unit,
    widthClass: WidthClass = LocalWidthClass.current,
) {
    val selected = state.selection
    if (widthClass != WidthClass.EXPANDED) {
        // An item chosen on a wide window that has since narrowed (rotated, folded, resized) opens
        // as its own screen, once: the selection is gone before the screen opens. Only while the
        // history is resumed, so returning from an editor opened beside the list lands on the item.
        if (selected != null) {
            LifecycleResumeEffect(selected) {
                onClearSelection()
                when (selected) {
                    is GiftHistorySelection.Gift -> onOpenGift(selected.id)
                    is GiftHistorySelection.Idea -> onOpenGiftIdea(selected.id)
                }
                onPauseOrDispose {}
            }
        }
        GiftHistoryScreen(
            state = state,
            onBack = onBack,
            onFilterChange = onFilterChange,
            onAddGift = onAddGift,
            onOpenGift = { onOpenGift(it.gift.id) },
            onAddGiftIdea = onAddGiftIdea,
            onOpenGiftIdea = { onOpenGiftIdea(it.idea.id) },
        )
        return
    }

    // Back first closes the item beside the list, then leaves the history as usual.
    BackHandler(enabled = selected != null, onBack = onClearSelection)
    ListDetailPanes(
        list = { paneModifier ->
            GiftHistoryScreen(
                state = state,
                onBack = onBack,
                onFilterChange = onFilterChange,
                onAddGift = onAddGift,
                onOpenGift = onSelectGift,
                onAddGiftIdea = onAddGiftIdea,
                onOpenGiftIdea = onSelectIdea,
                modifier = paneModifier,
                selectable = true,
            )
        },
        detail = {
            when {
                // Keyed, so each item gets its own scroll position.
                selected != null -> key(selected) {
                    when (selected) {
                        is GiftHistorySelection.Gift -> giftDetail(selected.id, onClearSelection)
                        is GiftHistorySelection.Idea -> ideaDetail(selected.id, onClearSelection)
                    }
                }
                // Only an invitation when there is something to choose.
                state.content is GiftHistoryContent.Loaded ->
                    MessageContent(message = stringResource(R.string.gifts_select_gift))
                state.content is GiftHistoryContent.IdeasLoaded ->
                    MessageContent(message = stringResource(R.string.gifts_select_idea))
            }
        },
    )
}

@Composable
private fun AdaptiveGiftHistoryPreview(selected: GiftEntry?) {
    GiftiaryTheme {
        ProvideWidthClass {
            AdaptiveGiftHistoryScreen(
                state = GiftHistoryUiState(
                    filter = GiftFilter.ALL,
                    content = GiftHistoryContent.Loaded(previewGifts),
                    selection = selected?.let { GiftHistorySelection.Gift(it.gift.id) },
                ),
                onBack = null,
                onFilterChange = {},
                onAddGift = {},
                onOpenGift = {},
                onAddGiftIdea = {},
                onOpenGiftIdea = {},
                onSelectGift = {},
                onSelectIdea = {},
                onClearSelection = {},
                giftDetail = { id, _ ->
                    GiftDetailScreen(
                        state = GiftDetailUiState(content = GiftDetailContent.Loaded(previewGifts.first { it.gift.id == id })),
                        onBack = null,
                        onEditGift = {},
                        onDeleteRequested = {},
                        onDeleteConfirmed = {},
                        onDeleteDismissed = {},
                        onDeleteErrorShown = {},
                    )
                },
                ideaDetail = { _, _ -> },
            )
        }
    }
}

@PreviewLightDark
@GiftiaryAdaptivePreviews
@Composable
private fun AdaptiveGiftHistorySelectedPreview() {
    AdaptiveGiftHistoryPreview(selected = previewGifts.first())
}

@GiftiaryAdaptivePreviews
@Composable
private fun AdaptiveGiftHistoryNothingSelectedPreview() {
    AdaptiveGiftHistoryPreview(selected = null)
}
