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

@Composable
fun GiftyNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            HomeScreen(onOpenPeople = dropUnlessResumed { navController.navigate(PeopleRoute) })
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
    }
}

/** Route ids are strings; a malformed one closes the destination instead of crashing. */
@Composable
private fun WithUuid(raw: String, navController: NavHostController, content: @Composable (UUID) -> Unit) {
    val id = runCatching { UUID.fromString(raw) }.getOrNull()
    if (id != null) {
        content(id)
    } else {
        LaunchedEffect(raw) { navController.popBackStack() }
    }
}

// Ignores repeated taps while the screen is already leaving, which would otherwise pop twice.
@Composable
private fun NavHostController.popBackStackUnlessPaused(): () -> Unit = dropUnlessResumed { popBackStack() }
