package app.giftiary.presentation.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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

/** People and a person's detail and editor. */
internal fun NavGraphBuilder.peopleDestinations(navController: NavHostController) {
    composable<PeopleRoute> {
        PeopleScreen(
            onBack = navController.popBackStackUnlessPaused(),
            onAddPerson = dropUnlessResumed { navController.navigate(AddPersonRoute) },
            onOpenPerson = { personId -> navController.navigate(PersonDetailRoute(personId.toString())) },
        )
    }
    composable<PersonDetailRoute> { entry ->
        WithUuid(entry.toRoute<PersonDetailRoute>().personId, navController) { personId ->
            PersonDetailScreen(
                viewModel = hiltViewModel<PersonDetailViewModel, PersonDetailViewModel.Factory> { factory ->
                    factory.create(personId)
                },
                onBack = navController.popBackStackUnlessPaused(),
                onEditPerson = { id -> navController.navigate(EditPersonRoute(id.toString())) },
                onAddOccasion = { id -> navController.navigate(AddOccasionRoute(id.toString())) },
                onEditOccasion = { id -> navController.navigate(EditOccasionRoute(id.toString())) },
                onAddGift = { id -> navController.navigate(AddGiftRoute(personId = id.toString())) },
                onOpenGift = { id -> navController.navigate(GiftDetailRoute(id.toString())) },
                onAddGiftIdea = { id -> navController.navigate(AddGiftIdeaRoute(personId = id.toString())) },
                onOpenGiftIdea = { id -> navController.navigate(GiftIdeaDetailRoute(id.toString())) },
                onAddWishlist = { id -> navController.navigate(AddWishlistRoute(personId = id.toString())) },
                onOpenWishlist = { id -> navController.navigate(WishlistDetailRoute(id.toString())) },
            )
        }
    }
    composable<AddPersonRoute> {
        PersonEditorScreen(
            viewModel = hiltViewModel<PersonEditorViewModel, PersonEditorViewModel.Factory> { factory ->
                factory.create(personId = null)
            },
            onClose = navController.popBackStackUnlessPaused(),
        )
    }
    composable<EditPersonRoute> { entry ->
        WithUuid(entry.toRoute<EditPersonRoute>().personId, navController) { personId ->
            PersonEditorScreen(
                viewModel = hiltViewModel<PersonEditorViewModel, PersonEditorViewModel.Factory> { factory ->
                    factory.create(personId)
                },
                onClose = navController.popBackStackUnlessPaused(),
            )
        }
    }
}
