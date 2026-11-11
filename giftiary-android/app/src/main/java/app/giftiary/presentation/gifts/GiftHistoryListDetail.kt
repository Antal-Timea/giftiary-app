package app.giftiary.presentation.gifts

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.giftiary.R
import app.giftiary.domain.repository.GiftEntry
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
 * beside it; narrower windows open it as a screen of its own, as on a phone.
 */
@Composable
internal fun AdaptiveGiftHistoryScreen(
    state: GiftHistoryUiState,
    photos: ShownPhotos,
    onFilterChange: (GiftFilter) -> Unit,
    onQueryChange: (String) -> Unit,
    onAddGift: () -> Unit,
    onOpenGift: (UUID) -> Unit,
    onSelectGift: (GiftEntry) -> Unit,
    onClearSelection: () -> Unit,
    giftDetail: @Composable (id: UUID, onDeleted: () -> Unit) -> Unit,
    widthClass: WidthClass = LocalWidthClass.current,
) {
    val selected = state.selectedGiftId
    if (widthClass != WidthClass.EXPANDED) {
        // A gift chosen on a wide window that has since narrowed (rotated, folded, resized) opens
        // as its own screen, once: the selection is gone before the screen opens. Only while the
        // history is resumed, so returning from an editor opened beside the list lands on the gift.
        if (selected != null) {
            LifecycleResumeEffect(selected) {
                onClearSelection()
                onOpenGift(selected)
                onPauseOrDispose {}
            }
        }
        GiftHistoryScreen(
            state = state,
            photos = photos,
            onFilterChange = onFilterChange,
            onQueryChange = onQueryChange,
            onAddGift = onAddGift,
            onOpenGift = { onOpenGift(it.gift.id) },
        )
        return
    }

    // Back first closes the gift beside the list, then leaves the history as usual.
    BackHandler(enabled = selected != null, onBack = onClearSelection)
    ListDetailPanes(
        list = { paneModifier ->
            GiftHistoryScreen(
                state = state,
                photos = photos,
                onFilterChange = onFilterChange,
                onQueryChange = onQueryChange,
                onAddGift = onAddGift,
                onOpenGift = onSelectGift,
                modifier = paneModifier,
                selectable = true,
            )
        },
        detail = {
            when {
                // Keyed, so each gift gets its own scroll position.
                selected != null -> key(selected) { giftDetail(selected, onClearSelection) }
                // Only an invitation when there is something to choose.
                state.content is GiftHistoryContent.Loaded ->
                    MessageContent(message = stringResource(R.string.gifts_select_gift))
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
                    content = GiftHistoryContent.Loaded(groupByYearAndMonth(previewGifts)),
                    selectedGiftId = selected?.gift?.id,
                ),
                photos = ShownPhotos.of(emptyMap()),
                onFilterChange = {},
                onQueryChange = {},
                onAddGift = {},
                onOpenGift = {},
                onSelectGift = {},
                onClearSelection = {},
                giftDetail = { id, _ ->
                    GiftDetailScreen(
                        state = GiftDetailUiState(content = GiftDetailContent.Loaded(previewGifts.first { it.gift.id == id })),
                        onBack = null,
                        onEditGift = {},
                        onOpenPerson = {},
                        onDeleteRequested = {},
                        onDeleteConfirmed = {},
                        onDeleteDismissed = {},
                        onDeleteErrorShown = {},
                    )
                },
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
