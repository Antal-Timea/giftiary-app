package app.giftiary.presentation.navigation

import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.giftiary.presentation.occasions.OccasionEditorScreen
import app.giftiary.presentation.occasions.OccasionEditorTarget
import app.giftiary.presentation.occasions.OccasionEditorViewModel
import app.giftiary.presentation.occasions.OccasionPlanScreen
import app.giftiary.presentation.occasions.OccasionPlanViewModel
import app.giftiary.presentation.occasions.OwnOccasionsScreen

/** Occasions: the user's own, an occasion's plan, and the editor. */
internal fun NavGraphBuilder.occasionDestinations(navController: NavHostController) {
    composable<AddOccasionRoute> { entry ->
        val raw = entry.toRoute<AddOccasionRoute>().personId
        val target = OccasionEditorTarget.newFor(raw)
        if (target != null) {
            OccasionEditorScreen(
                viewModel = hiltViewModel<OccasionEditorViewModel, OccasionEditorViewModel.Factory> { factory ->
                    factory.create(target)
                },
                onClose = navController.popBackStackUnlessPaused(),
            )
        } else {
            LaunchedEffect(raw) { navController.popBackStack() }
        }
    }
    composable<OwnOccasionsRoute> {
        OwnOccasionsScreen(
            onBack = navController.popBackStackUnlessPaused(),
            onAddOccasion = dropUnlessResumed { navController.navigate(AddOccasionRoute()) },
            onOpenOccasion = { id -> navController.navigate(occasionPlanRoute(id)) },
        )
    }
    composable<OccasionPlanRoute> { entry ->
        WithUuid(entry.toRoute<OccasionPlanRoute>().occasionId, navController) { occasionId ->
            OccasionPlanScreen(
                viewModel = hiltViewModel<OccasionPlanViewModel, OccasionPlanViewModel.Factory> { factory ->
                    factory.create(occasionId)
                },
                onBack = navController.popBackStackUnlessPaused(),
                onEditOccasion = { id -> navController.navigate(EditOccasionRoute(id.toString())) },
                onOpenPerson = { id -> navController.navigate(PersonDetailRoute(id.toString())) },
                onOpenGiftIdea = { id -> navController.navigate(GiftIdeaDetailRoute(id.toString())) },
                onAddGiftIdea = { occasion -> navController.navigate(addIdeaRoute(occasion)) },
            )
        }
    }
    composable<EditOccasionRoute> { entry ->
        WithUuid(entry.toRoute<EditOccasionRoute>().occasionId, navController) { occasionId ->
            OccasionEditorScreen(
                viewModel = hiltViewModel<OccasionEditorViewModel, OccasionEditorViewModel.Factory> { factory ->
                    factory.create(OccasionEditorTarget.Existing(occasionId))
                },
                onClose = navController.popBackStackUnlessPaused(),
            )
        }
    }
}
