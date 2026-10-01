package app.gifty.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.gifty.domain.model.GiftDirection
import app.gifty.presentation.giftideas.GiftIdeaDetailScreen
import app.gifty.presentation.giftideas.GiftIdeaDetailViewModel
import app.gifty.presentation.giftideas.GiftIdeaEditorScreen
import app.gifty.presentation.giftideas.GiftIdeaEditorTarget
import app.gifty.presentation.giftideas.GiftIdeaEditorViewModel
import app.gifty.presentation.giftideas.MarkGiftIdeaGivenScreen
import app.gifty.presentation.giftideas.MarkGiftIdeaGivenViewModel
import app.gifty.presentation.gifts.GiftDetailScreen
import app.gifty.presentation.gifts.GiftDetailViewModel
import app.gifty.presentation.gifts.GiftEditorScreen
import app.gifty.presentation.gifts.GiftEditorTarget
import app.gifty.presentation.gifts.GiftEditorViewModel
import app.gifty.presentation.gifts.GiftHistoryScreen
import app.gifty.presentation.home.HomeScreen
import app.gifty.presentation.occasions.OccasionEditorScreen
import app.gifty.presentation.occasions.OccasionEditorTarget
import app.gifty.presentation.occasions.OccasionEditorViewModel
import app.gifty.presentation.people.PeopleScreen
import app.gifty.presentation.people.PersonDetailScreen
import app.gifty.presentation.people.PersonDetailViewModel
import app.gifty.presentation.people.PersonEditorScreen
import app.gifty.presentation.people.PersonEditorViewModel
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object HomeRoute

@Serializable
data object PeopleRoute

@Serializable
data class PersonDetailRoute(val personId: String)

@Serializable
data object AddPersonRoute

@Serializable
data class EditPersonRoute(val personId: String)

@Serializable
data class AddOccasionRoute(val personId: String)

@Serializable
data class EditOccasionRoute(val occasionId: String)

@Serializable
data object GiftHistoryRoute

@Serializable
data class GiftDetailRoute(val giftId: String)

/** Both arguments only preselect the editor; [direction] is a [GiftDirection] name. */
@Serializable
data class AddGiftRoute(val direction: String? = null, val personId: String? = null)

@Serializable
data class EditGiftRoute(val giftId: String)

@Serializable
data class GiftIdeaDetailRoute(val giftIdeaId: String)

/** [personId] only preselects the recipient. */
@Serializable
data class AddGiftIdeaRoute(val personId: String? = null)

@Serializable
data class EditGiftIdeaRoute(val giftIdeaId: String)

@Serializable
data class MarkGiftIdeaGivenRoute(val giftIdeaId: String)

@Composable
fun GiftyNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(
                onOpenGifts = dropUnlessResumed { navController.navigate(GiftHistoryRoute) },
                onOpenPeople = dropUnlessResumed { navController.navigate(PeopleRoute) },
            )
        }
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
        composable<AddOccasionRoute> { entry ->
            WithUuid(entry.toRoute<AddOccasionRoute>().personId, navController) { personId ->
                OccasionEditorScreen(
                    viewModel = hiltViewModel<OccasionEditorViewModel, OccasionEditorViewModel.Factory> { factory ->
                        factory.create(OccasionEditorTarget.New(personId))
                    },
                    onClose = navController.popBackStackUnlessPaused(),
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
        composable<GiftHistoryRoute> {
            GiftHistoryScreen(
                onBack = navController.popBackStackUnlessPaused(),
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
            // A malformed preselection is ignored; the editor works without it.
            val personId = entry.toRoute<AddGiftIdeaRoute>().personId?.toUuidOrNull()
            GiftIdeaEditorScreen(
                viewModel = hiltViewModel<GiftIdeaEditorViewModel, GiftIdeaEditorViewModel.Factory> { factory ->
                    factory.create(GiftIdeaEditorTarget.New(personId))
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
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

/** Route ids are strings; a malformed one closes the destination instead of crashing. */
@Composable
private fun WithUuid(raw: String, navController: NavHostController, content: @Composable (UUID) -> Unit) {
    val id = raw.toUuidOrNull()
    if (id != null) {
        content(id)
    } else {
        LaunchedEffect(raw) { navController.popBackStack() }
    }
}

// Ignores repeated taps while the screen is already leaving, which would otherwise pop twice.
@Composable
private fun NavHostController.popBackStackUnlessPaused(): () -> Unit = dropUnlessResumed { popBackStack() }
