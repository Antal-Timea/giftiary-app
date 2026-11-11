package app.giftiary.presentation.people

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.giftiary.R
import app.giftiary.domain.model.Person
import app.giftiary.presentation.components.GiftiaryAdaptivePreviews
import app.giftiary.presentation.components.ListDetailPanes
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.gifts.ShownPhotos
import app.giftiary.presentation.home.ComingUpContent
import app.giftiary.presentation.navigation.hasNavigationRail
import app.giftiary.presentation.theme.GiftiaryTheme
import java.util.UUID

/**
 * People at any width. A wide window keeps the list in view and shows the selected person beside
 * it; narrower windows open a person as a screen of their own, as on a phone.
 */
@Composable
internal fun AdaptivePeopleScreen(
    state: PeopleUiState,
    photos: ShownPhotos,
    comingUp: ComingUpContent,
    onQueryChange: (String) -> Unit,
    onAddPerson: () -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onSelectPerson: (Person) -> Unit,
    onClearSelection: () -> Unit,
    personDetail: @Composable (UUID) -> Unit,
    widthClass: WidthClass = LocalWidthClass.current,
) {
    val selected = state.selectedPersonId
    if (widthClass != WidthClass.EXPANDED) {
        // A person chosen on a wide window that has since narrowed (rotated, folded) opens as their
        // own screen, once: the selection is gone before the screen opens. Only while People is
        // resumed, so returning from an editor opened beside the list lands on that person.
        if (selected != null) {
            LifecycleResumeEffect(selected) {
                onClearSelection()
                onOpenPerson(selected)
                onPauseOrDispose {}
            }
        }
        PeopleScreen(
            state = state,
            photos = photos,
            comingUp = comingUp,
            onQueryChange = onQueryChange,
            onAddPerson = onAddPerson,
            onOpenPerson = { onOpenPerson(it.id) },
            showAddButton = widthClass.hasNavigationRail,
        )
        return
    }

    // Back first closes the person beside the list, then leaves People as usual.
    BackHandler(enabled = selected != null, onBack = onClearSelection)
    ListDetailPanes(
        list = { paneModifier ->
            PeopleScreen(
                state = state,
                photos = photos,
                comingUp = comingUp,
                onQueryChange = onQueryChange,
                onAddPerson = onAddPerson,
                onOpenPerson = onSelectPerson,
                modifier = paneModifier,
                selectable = true,
                showAddButton = widthClass.hasNavigationRail,
            )
        },
        detail = {
            val hasPeople = (state.content as? PeopleContent.Loaded)?.people?.isNotEmpty() == true
            when {
                // Keyed, so each person gets their own scroll position.
                selected != null -> key(selected) { personDetail(selected) }
                // Only an invitation when there is someone to choose.
                hasPeople -> MessageContent(message = stringResource(R.string.people_select_person))
            }
        },
    )
}

@Composable
private fun AdaptivePeoplePreview(selected: Person?) {
    GiftiaryTheme {
        ProvideWidthClass {
            AdaptivePeopleScreen(
                state = PeopleUiState(content = PeopleContent.Loaded(previewPeople), selectedPersonId = selected?.id),
                photos = ShownPhotos.of(emptyMap()),
                comingUp = ComingUpContent.Loaded(emptyList()),
                onQueryChange = {},
                onAddPerson = {},
                onOpenPerson = {},
                onSelectPerson = {},
                onClearSelection = {},
                personDetail = { id ->
                    PersonDetailScreen(
                        state = PersonDetailUiState.Loaded(
                            person = previewPeople.first { it.id == id },
                            occasions = emptyList(),
                            giftIdeas = emptyList(),
                            gifts = emptyList(),
                            wishlists = emptyList(),
                        ),
                        onBack = null,
                        onEditPerson = {},
                        onAddOccasion = {},
                        onEditOccasion = {},
                        onPlanGift = {},
                        onAddGift = {},
                        onOpenGift = {},
                        onAddGiftIdea = {},
                        onOpenGiftIdea = {},
                        onAddWishlist = {},
                        onOpenWishlist = {},
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
private fun AdaptivePeopleSelectedPreview() {
    AdaptivePeoplePreview(selected = previewPeople.first())
}

@GiftiaryAdaptivePreviews
@Composable
private fun AdaptivePeopleNothingSelectedPreview() {
    AdaptivePeoplePreview(selected = null)
}
