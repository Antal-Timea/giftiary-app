package app.giftiary.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.giftiary.domain.model.GiftDirection
import app.giftiary.presentation.giftideas.GiftIdeaDetailScreen
import app.giftiary.presentation.giftideas.GiftIdeaDetailViewModel
import app.giftiary.presentation.giftideas.GiftIdeaEditorScreen
import app.giftiary.presentation.giftideas.GiftIdeaEditorTarget
import app.giftiary.presentation.giftideas.GiftIdeaEditorViewModel
import app.giftiary.presentation.giftideas.MarkGiftIdeaGivenScreen
import app.giftiary.presentation.giftideas.MarkGiftIdeaGivenViewModel
import app.giftiary.presentation.gifts.GiftDetailScreen
import app.giftiary.presentation.gifts.GiftDetailViewModel
import app.giftiary.presentation.gifts.GiftEditorScreen
import app.giftiary.presentation.gifts.GiftEditorTarget
import app.giftiary.presentation.gifts.GiftEditorViewModel
import app.giftiary.presentation.gifts.GiftHistoryScreen
import java.util.UUID

/** Gift history, gifts and gift ideas, including marking an idea as given. */
internal fun NavGraphBuilder.giftDestinations(navController: NavHostController) {
    composable<GiftHistoryRoute> {
        // A section root: the navigation bar or rail leads away from it, so it has no back arrow.
        GiftHistoryScreen(
            onAddGift = dropUnlessResumed { navController.navigate(AddGiftRoute()) },
            onOpenGift = { id -> navController.navigate(GiftDetailRoute(id.toString())) },
            // Beside the list on a wide window: one view model per gift, kept in the history's entry.
            giftDetail = { id, onDeleted ->
                GiftDetail(id, navController, onBack = null, onDeleted = onDeleted, viewModelKey = "gift-$id")
            },
        )
    }
    composable<GiftDetailRoute> { entry ->
        WithUuid(entry.toRoute<GiftDetailRoute>().giftId, navController) { giftId ->
            val close = navController.popBackStackUnlessPaused()
            GiftDetail(giftId, navController, onBack = close, onDeleted = close)
        }
    }
    composable<AddGiftRoute> { entry ->
        val route = entry.toRoute<AddGiftRoute>()
        // Malformed preselections are ignored; the editor works without them.
        val target = GiftEditorTarget.New(
            direction = GiftDirection.entries.find { it.name == route.direction },
            personId = route.personId?.toUuidOrNull(),
        )
        GiftEditorScreen(
            viewModel = hiltViewModel<GiftEditorViewModel, GiftEditorViewModel.Factory> { factory ->
                factory.create(target)
            },
            onClose = navController.popBackStackUnlessPaused(),
            onOpenPeople = { navController.navigate(PeopleRoute) },
            onAddPerson = dropUnlessResumed { navController.navigate(AddPersonRoute) },
        )
    }
    composable<EditGiftRoute> { entry ->
        WithUuid(entry.toRoute<EditGiftRoute>().giftId, navController) { giftId ->
            GiftEditorScreen(
                viewModel = hiltViewModel<GiftEditorViewModel, GiftEditorViewModel.Factory> { factory ->
                    factory.create(GiftEditorTarget.Existing(giftId))
                },
                onClose = navController.popBackStackUnlessPaused(),
                onOpenPeople = { navController.navigate(PeopleRoute) },
                onAddPerson = dropUnlessResumed { navController.navigate(AddPersonRoute) },
                onDeleted = dropUnlessResumed { navController.leaveDeletedEditor<GiftDetailRoute>() },
            )
        }
    }
    composable<GiftIdeaDetailRoute> { entry ->
        WithUuid(entry.toRoute<GiftIdeaDetailRoute>().giftIdeaId, navController) { giftIdeaId ->
            val close = navController.popBackStackUnlessPaused()
            GiftIdeaDetail(giftIdeaId, navController, onBack = close, onDeleted = close)
        }
    }
    composable<AddGiftIdeaRoute> { entry ->
        val route = entry.toRoute<AddGiftIdeaRoute>()
        GiftIdeaEditorScreen(
            viewModel = hiltViewModel<GiftIdeaEditorViewModel, GiftIdeaEditorViewModel.Factory> { factory ->
                factory.create(GiftIdeaEditorTarget.newFor(route.personId, route.occasionId))
            },
            onClose = navController.popBackStackUnlessPaused(),
            onOpenPeople = { navController.navigate(PeopleRoute) },
        )
    }
    composable<EditGiftIdeaRoute> { entry ->
        WithUuid(entry.toRoute<EditGiftIdeaRoute>().giftIdeaId, navController) { giftIdeaId ->
            GiftIdeaEditorScreen(
                viewModel = hiltViewModel<GiftIdeaEditorViewModel, GiftIdeaEditorViewModel.Factory> { factory ->
                    factory.create(GiftIdeaEditorTarget.Existing(giftIdeaId))
                },
                onClose = navController.popBackStackUnlessPaused(),
                onOpenPeople = { navController.navigate(PeopleRoute) },
                onDeleted = dropUnlessResumed { navController.leaveDeletedEditor<GiftIdeaDetailRoute>() },
            )
        }
    }
    composable<MarkGiftIdeaGivenRoute> { entry ->
        WithUuid(entry.toRoute<MarkGiftIdeaGivenRoute>().giftIdeaId, navController) { giftIdeaId ->
            MarkGiftIdeaGivenScreen(
                viewModel = hiltViewModel<MarkGiftIdeaGivenViewModel, MarkGiftIdeaGivenViewModel.Factory> { factory ->
                    factory.create(giftIdeaId)
                },
                onBack = navController.popBackStackUnlessPaused(),
                onGiven = { giftId -> navController.openGivenGift(giftId) },
            )
        }
    }
}

/** A gift's detail, as its own screen or beside the history list; its actions open the same routes either way. */
@Composable
private fun GiftDetail(
    giftId: UUID,
    navController: NavHostController,
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
    viewModelKey: String? = null,
) {
    GiftDetailScreen(
        viewModel = hiltViewModel<GiftDetailViewModel, GiftDetailViewModel.Factory>(key = viewModelKey) { factory ->
            factory.create(giftId)
        },
        onBack = onBack,
        onDeleted = onDeleted,
        onEditGift = { id -> navController.navigate(EditGiftRoute(id.toString())) },
        onOpenPerson = { id -> navController.navigate(PersonDetailRoute(id.toString())) },
    )
}

/** An idea's detail, as its own screen or beside the history list; its actions open the same routes either way. */
@Composable
private fun GiftIdeaDetail(
    giftIdeaId: UUID,
    navController: NavHostController,
    onBack: (() -> Unit)?,
    onDeleted: () -> Unit,
    viewModelKey: String? = null,
) {
    GiftIdeaDetailScreen(
        viewModel = hiltViewModel<GiftIdeaDetailViewModel, GiftIdeaDetailViewModel.Factory>(key = viewModelKey) { factory ->
            factory.create(giftIdeaId)
        },
        onBack = onBack,
        onDeleted = onDeleted,
        onEditGiftIdea = { id -> navController.navigate(EditGiftIdeaRoute(id.toString())) },
        onMarkAsGiven = { id -> navController.navigate(MarkGiftIdeaGivenRoute(id.toString())) },
    )
}

/**
 * After an idea is marked as given: the idea is archived, so show the gift it became and drop the
 * finished steps. Opened from the idea's own screen, that screen goes too, as always; opened from
 * beside the history list, only the marking does, and Back returns to the history.
 */
internal fun NavController.openGivenGift(giftId: UUID) {
    val fromIdeaScreen = previousBackStackEntry?.destination?.hasRoute<GiftIdeaDetailRoute>() == true
    navigate(GiftDetailRoute(giftId.toString())) {
        if (fromIdeaScreen) {
            popUpTo<GiftIdeaDetailRoute> { inclusive = true }
        } else {
            popUpTo<MarkGiftIdeaGivenRoute> { inclusive = true }
        }
    }
}
