package app.giftiary.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.giftiary.presentation.people.PeopleScreen
import app.giftiary.presentation.people.PersonDetailScreen
import app.giftiary.presentation.people.PersonDetailViewModel
import app.giftiary.presentation.people.PersonEditorScreen
import app.giftiary.presentation.people.PersonEditorViewModel
import java.util.UUID

/** People and a person's detail and editor. */
internal fun NavGraphBuilder.peopleDestinations(navController: NavHostController) {
    composable<PeopleRoute> {
        PeopleScreen(
            onAddPerson = dropUnlessResumed { navController.navigate(AddPersonRoute()) },
            onOpenPerson = { personId -> navController.navigate(PersonDetailRoute(personId.toString())) },
            // Beside the list on a wide window: one view model per person, kept in People's entry.
            personDetail = { personId -> PersonDetail(personId, navController, onBack = null, viewModelKey = "person-$personId") },
        )
    }
    composable<PersonDetailRoute> { entry ->
        WithUuid(entry.toRoute<PersonDetailRoute>().personId, navController) { personId ->
            PersonDetail(personId, navController, onBack = navController.popBackStackUnlessPaused())
        }
    }
    composable<AddPersonRoute> { entry ->
        val openWhenSaved = entry.toRoute<AddPersonRoute>().openWhenSaved
        PersonEditorScreen(
            viewModel = hiltViewModel<PersonEditorViewModel, PersonEditorViewModel.Factory> { factory ->
                factory.create(personId = null)
            },
            onClose = navController.popBackStackUnlessPaused(),
            // The new person's page replaces the editor, so Back returns to where adding began;
            // that page is where their occasions are added.
            onCreated = if (openWhenSaved) {
                { personId: UUID ->
                    // As dropUnlessResumed does for the other exits: never twice, never from a page left behind.
                    if (entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) navController.openCreatedPerson(personId)
                }
            } else {
                null
            },
        )
    }
    composable<EditPersonRoute> { entry ->
        WithUuid(entry.toRoute<EditPersonRoute>().personId, navController) { personId ->
            PersonEditorScreen(
                viewModel = hiltViewModel<PersonEditorViewModel, PersonEditorViewModel.Factory> { factory ->
                    factory.create(personId)
                },
                onClose = navController.popBackStackUnlessPaused(),
                onDeleted = dropUnlessResumed { navController.leaveDeletedEditor<PersonDetailRoute>() },
            )
        }
    }
}

/** A person's detail, as its own screen or beside the People list; its actions open the same routes either way. */
@Composable
private fun PersonDetail(
    personId: UUID,
    navController: NavHostController,
    onBack: (() -> Unit)?,
    viewModelKey: String? = null,
) {
    PersonDetailScreen(
        viewModel = hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory>(key = viewModelKey) { factory ->
            factory.create(personId)
        },
        onBack = onBack,
        onEditPerson = { id -> navController.navigate(EditPersonRoute(id.toString())) },
        onAddOccasion = { id -> navController.navigate(AddOccasionRoute(id.toString())) },
        onEditOccasion = { id -> navController.navigate(EditOccasionRoute(id.toString())) },
        onPlanGift = { id -> navController.navigate(OccasionPlanRoute(id.toString())) },
        onAddGift = { id -> navController.navigate(AddGiftRoute(personId = id.toString())) },
        onOpenGift = { id -> navController.navigate(GiftDetailRoute(id.toString())) },
        onAddGiftIdea = { id -> navController.navigate(AddGiftIdeaRoute(personId = id.toString())) },
        onOpenGiftIdea = { id -> navController.navigate(GiftIdeaDetailRoute(id.toString())) },
        onAddWishlist = { id -> navController.navigate(AddWishlistRoute(personId = id.toString())) },
        onOpenWishlist = { id -> navController.navigate(WishlistDetailRoute(id.toString())) },
        // Beside the list, People forgets a selected person who is gone; on its own the screen closes.
        onDeleted = onBack ?: {},
    )
}

internal fun NavHostController.openCreatedPerson(personId: UUID) {
    navigate(PersonDetailRoute(personId.toString())) {
        popUpTo<AddPersonRoute> { inclusive = true }
    }
}
