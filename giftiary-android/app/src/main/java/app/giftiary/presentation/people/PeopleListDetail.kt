package app.giftiary.presentation.people

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.giftiary.R
import app.giftiary.domain.model.Person
import app.giftiary.presentation.components.GiftiaryAdaptivePreviews
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.components.MessageContent
import app.giftiary.presentation.components.ProvideWidthClass
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.theme.GiftiaryLayout
import app.giftiary.presentation.theme.GiftiaryTheme
import java.time.Instant
import java.util.UUID

/**
 * People at any width. A wide window keeps the list in view and shows the selected person beside
 * it; narrower windows open a person as a screen of their own, as on a phone.
 */
@Composable
internal fun AdaptivePeopleScreen(
    state: PeopleUiState,
    onBack: (() -> Unit)?,
    onAddPerson: () -> Unit,
    onOpenPerson: (UUID) -> Unit,
    onSelectPerson: (Person) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteRequested: (Person) -> Unit,
    onDeleteConfirmed: () -> Unit,
    onDeleteDismissed: () -> Unit,
    onDeleteErrorShown: () -> Unit,
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
            onBack = onBack,
            onAddPerson = onAddPerson,
            onOpenPerson = { onOpenPerson(it.id) },
            onDeleteRequested = onDeleteRequested,
            onDeleteConfirmed = onDeleteConfirmed,
            onDeleteDismissed = onDeleteDismissed,
            onDeleteErrorShown = onDeleteErrorShown,
        )
        return
    }

    // Back first closes the person beside the list, then leaves People as usual.
    BackHandler(enabled = selected != null, onBack = onClearSelection)
    Row(Modifier.fillMaxSize()) {
        PeopleScreen(
            state = state,
            onBack = onBack,
            onAddPerson = onAddPerson,
            onOpenPerson = onSelectPerson,
            onDeleteRequested = onDeleteRequested,
            onDeleteConfirmed = onDeleteConfirmed,
            onDeleteDismissed = onDeleteDismissed,
            onDeleteErrorShown = onDeleteErrorShown,
            modifier = Modifier
                .width(GiftiaryLayout.listPaneWidth)
                .fillMaxHeight(),
            selectable = true,
        )
        VerticalDivider(color = GiftiaryTheme.colors.divider)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(GiftiaryTheme.colors.background),
        ) {
            val hasPeople = (state.content as? PeopleContent.Loaded)?.people?.isNotEmpty() == true
            when {
                // Keyed, so each person gets their own scroll position.
                selected != null -> key(selected) { personDetail(selected) }
                // Only an invitation when there is someone to choose.
                hasPeople -> MessageContent(message = stringResource(R.string.people_select_person))
            }
        }
    }
}

private val previewPeople = listOf(
    Person(UUID.randomUUID(), "Anna Kovács", "Sister", null, null, Instant.EPOCH, Instant.EPOCH),
    Person(UUID.randomUUID(), "Péter", null, null, null, Instant.EPOCH, Instant.EPOCH),
    Person(UUID.randomUUID(), "Grandma Ilona", "Grandmother", null, null, Instant.EPOCH, Instant.EPOCH),
)

@Composable
private fun AdaptivePeoplePreview(selected: Person?) {
    GiftiaryTheme {
        ProvideWidthClass {
            AdaptivePeopleScreen(
                state = PeopleUiState(content = PeopleContent.Loaded(previewPeople), selectedPersonId = selected?.id),
                onBack = null,
                onAddPerson = {},
                onOpenPerson = {},
                onSelectPerson = {},
                onClearSelection = {},
                onDeleteRequested = {},
                onDeleteConfirmed = {},
                onDeleteDismissed = {},
                onDeleteErrorShown = {},
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
                        onAddGift = {},
                        onOpenGift = {},
                        onAddGiftIdea = {},
                        onOpenGiftIdea = {},
                        onAddWishlist = {},
                        onOpenWishlist = {},
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
