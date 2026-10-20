package app.giftiary.presentation.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
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

/** Gift history, gifts and gift ideas, including marking an idea as given. */
internal fun NavGraphBuilder.giftDestinations(navController: NavHostController) {
    composable<GiftHistoryRoute> {
        GiftHistoryScreen(
            onBack = navController.sectionRootBack(),
            onAddGift = dropUnlessResumed { navController.navigate(AddGiftRoute()) },
            onOpenGift = { id -> navController.navigate(GiftDetailRoute(id.toString())) },
            onAddGiftIdea = dropUnlessResumed { navController.navigate(AddGiftIdeaRoute()) },
            onOpenGiftIdea = { id -> navController.navigate(GiftIdeaDetailRoute(id.toString())) },
        )
    }
    composable<GiftDetailRoute> { entry ->
        WithUuid(entry.toRoute<GiftDetailRoute>().giftId, navController) { giftId ->
            GiftDetailScreen(
                viewModel = hiltViewModel<GiftDetailViewModel, GiftDetailViewModel.Factory> { factory ->
                    factory.create(giftId)
                },
                onBack = navController.popBackStackUnlessPaused(),
                onEditGift = { id -> navController.navigate(EditGiftRoute(id.toString())) },
            )
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
            )
        }
    }
    composable<GiftIdeaDetailRoute> { entry ->
        WithUuid(entry.toRoute<GiftIdeaDetailRoute>().giftIdeaId, navController) { giftIdeaId ->
            GiftIdeaDetailScreen(
                viewModel = hiltViewModel<GiftIdeaDetailViewModel, GiftIdeaDetailViewModel.Factory> { factory ->
                    factory.create(giftIdeaId)
                },
                onBack = navController.popBackStackUnlessPaused(),
                onEditGiftIdea = { id -> navController.navigate(EditGiftIdeaRoute(id.toString())) },
                onMarkAsGiven = { id -> navController.navigate(MarkGiftIdeaGivenRoute(id.toString())) },
            )
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
                onGiven = { giftId ->
                    // The idea is archived now; show the gift it became instead of returning to it.
                    navController.navigate(GiftDetailRoute(giftId.toString())) {
                        popUpTo<GiftIdeaDetailRoute> { inclusive = true }
                    }
                },
            )
        }
    }
}
