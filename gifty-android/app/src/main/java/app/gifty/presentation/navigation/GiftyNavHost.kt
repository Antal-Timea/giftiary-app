package app.gifty.presentation.navigation

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.Occasion
import app.gifty.domain.model.SyncItem
import app.gifty.domain.model.SyncItemType
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
import app.gifty.presentation.home.ComingUpViewModel
import app.gifty.presentation.home.HomeScreen
import app.gifty.presentation.home.ReminderCardActions
import app.gifty.presentation.home.ReminderCardViewModel
import app.gifty.presentation.home.notificationSettingsIntent
import app.gifty.presentation.occasions.OccasionEditorScreen
import app.gifty.presentation.occasions.OccasionEditorTarget
import app.gifty.presentation.occasions.OccasionEditorViewModel
import app.gifty.presentation.occasions.OccasionPlanScreen
import app.gifty.presentation.occasions.OccasionPlanViewModel
import app.gifty.presentation.occasions.OwnOccasionsScreen
import app.gifty.presentation.people.PeopleScreen
import app.gifty.presentation.people.PersonDetailScreen
import app.gifty.presentation.people.PersonDetailViewModel
import app.gifty.presentation.people.PersonEditorScreen
import app.gifty.presentation.people.PersonEditorViewModel
import app.gifty.presentation.sync.SyncAttentionScreen
import app.gifty.presentation.sync.SyncStatusViewModel
import app.gifty.presentation.wishlists.WishDetailScreen
import app.gifty.presentation.wishlists.WishDetailViewModel
import app.gifty.presentation.wishlists.WishEditorScreen
import app.gifty.presentation.wishlists.WishEditorTarget
import app.gifty.presentation.wishlists.WishEditorViewModel
import app.gifty.presentation.wishlists.WishlistDetailScreen
import app.gifty.presentation.wishlists.WishlistDetailViewModel
import app.gifty.presentation.wishlists.WishlistEditorScreen
import app.gifty.presentation.wishlists.WishlistEditorTarget
import app.gifty.presentation.wishlists.WishlistEditorViewModel
import app.gifty.presentation.wishlists.WishlistsScreen
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object HomeRoute

@Serializable
data object PeopleRoute

@Serializable
data object SyncAttentionRoute

@Serializable
data class PersonDetailRoute(val personId: String)

@Serializable
data object AddPersonRoute

@Serializable
data class EditPersonRoute(val personId: String)

/** Without a person, a new occasion of the user's own. */
@Serializable
data class AddOccasionRoute(val personId: String? = null)

@Serializable
data object OwnOccasionsRoute

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
/** [personId] and [occasionId] preselect the new idea's recipient and occasion; a malformed one is ignored. */
@Serializable
data class AddGiftIdeaRoute(val personId: String? = null, val occasionId: String? = null)

@Serializable
data class OccasionPlanRoute(val occasionId: String)

/** Coming up and Your occasions open an occasion's plan. Person detail still opens its editor. */
internal fun occasionPlanRoute(occasionId: UUID) = OccasionPlanRoute(occasionId.toString())

/** A new idea from an occasion's plan: for the occasion, and for its person if it has one. */
internal fun addIdeaRoute(occasion: Occasion) =
    AddGiftIdeaRoute(personId = occasion.personId?.toString(), occasionId = occasion.id.toString())

@Serializable
data class EditGiftIdeaRoute(val giftIdeaId: String)

@Serializable
data class MarkGiftIdeaGivenRoute(val giftIdeaId: String)

@Serializable
data object WishlistsRoute

@Serializable
data class WishlistDetailRoute(val wishlistId: String)

/** [personId] only preselects who the new wishlist is for; a malformed or unavailable one is ignored. */
@Serializable
data class AddWishlistRoute(val personId: String? = null)

@Serializable
data class EditWishlistRoute(val wishlistId: String)

@Serializable
data class WishDetailRoute(val wishId: String)

@Serializable
data class AddWishRoute(val wishlistId: String)

@Serializable
data class EditWishRoute(val wishId: String)

@Composable
fun GiftyNavHost(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            val syncStatus by hiltViewModel<SyncStatusViewModel>().content.collectAsStateWithLifecycle()
            val comingUp by hiltViewModel<ComingUpViewModel>().content.collectAsStateWithLifecycle()
            val reminders = hiltViewModel<ReminderCardViewModel>()
            val reminderCard by reminders.state.collectAsStateWithLifecycle()
            // Coming back from the system's notification settings must show what was changed there.
            LifecycleResumeEffect(reminders) {
                reminders.onResume()
                onPauseOrDispose {}
            }
            val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                reminders.onPermissionResult()
            }
            val askToNotify = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            val context = LocalContext.current
            HomeScreen(
                syncStatus = syncStatus,
                comingUp = comingUp,
                reminderCard = reminderCard,
                reminderActions = ReminderCardActions(
                    onTurnOn = { if (reminders.onTurnOn()) askToNotify() },
                    onTurnOff = reminders::onTurnOff,
                    onAllow = askToNotify,
                    onOpenSettings = { context.startActivity(notificationSettingsIntent(context)) },
                ),
                onOpenComingUp = { occasionId -> navController.navigate(occasionPlanRoute(occasionId)) },
                onOpenGifts = dropUnlessResumed { navController.navigate(GiftHistoryRoute) },
                onOpenPeople = dropUnlessResumed { navController.navigate(PeopleRoute) },
                onOpenWishlists = dropUnlessResumed { navController.navigate(WishlistsRoute) },
                onOpenOwnOccasions = dropUnlessResumed { navController.navigate(OwnOccasionsRoute) },
                onOpenSyncAttention = dropUnlessResumed { navController.navigate(SyncAttentionRoute) },
                onSignOut = onSignOut,
            )
        }
        composable<SyncAttentionRoute> {
            SyncAttentionScreen(
                onBack = navController.popBackStackUnlessPaused(),
                onOpen = { item -> navController.navigate(routeOf(item)) },
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
        composable<WishlistsRoute> {
            WishlistsScreen(
                onBack = navController.popBackStackUnlessPaused(),
                onAddWishlist = dropUnlessResumed { navController.navigate(AddWishlistRoute()) },
                onOpenWishlist = { id -> navController.navigate(WishlistDetailRoute(id.toString())) },
            )
        }
        composable<AddWishlistRoute> { entry ->
            val target = WishlistEditorTarget.newFor(entry.toRoute<AddWishlistRoute>().personId)
            WishlistEditorScreen(
                viewModel = hiltViewModel<WishlistEditorViewModel, WishlistEditorViewModel.Factory> { factory ->
                    factory.create(target)
                },
                onClose = navController.popBackStackUnlessPaused(),
                onCreated = { id ->
                    // The new wishlist opens in place of the editor, so back returns to where it was created from.
                    navController.navigate(WishlistDetailRoute(id.toString())) {
                        popUpTo<AddWishlistRoute> { inclusive = true }
                    }
                },
            )
        }
        composable<EditWishlistRoute> { entry ->
            WithUuid(entry.toRoute<EditWishlistRoute>().wishlistId, navController) { wishlistId ->
                WishlistEditorScreen(
                    viewModel = hiltViewModel<WishlistEditorViewModel, WishlistEditorViewModel.Factory> { factory ->
                        factory.create(WishlistEditorTarget.Existing(wishlistId))
                    },
                    onClose = navController.popBackStackUnlessPaused(),
                    onCreated = {},
                )
            }
        }
        composable<WishlistDetailRoute> { entry ->
            WithUuid(entry.toRoute<WishlistDetailRoute>().wishlistId, navController) { wishlistId ->
                WishlistDetailScreen(
                    viewModel = hiltViewModel<WishlistDetailViewModel, WishlistDetailViewModel.Factory> { factory ->
                        factory.create(wishlistId)
                    },
                    onBack = navController.popBackStackUnlessPaused(),
                    onRename = { id -> navController.navigate(EditWishlistRoute(id.toString())) },
                    onOpenPerson = { id -> navController.navigate(PersonDetailRoute(id.toString())) },
                    onAddWish = { id -> navController.navigate(AddWishRoute(id.toString())) },
                    onOpenWish = { id -> navController.navigate(WishDetailRoute(id.toString())) },
                )
            }
        }
        composable<AddWishRoute> { entry ->
            WithUuid(entry.toRoute<AddWishRoute>().wishlistId, navController) { wishlistId ->
                WishEditorScreen(
                    viewModel = hiltViewModel<WishEditorViewModel, WishEditorViewModel.Factory> { factory ->
                        factory.create(WishEditorTarget.New(wishlistId))
                    },
                    onClose = navController.popBackStackUnlessPaused(),
                )
            }
        }
        composable<EditWishRoute> { entry ->
            WithUuid(entry.toRoute<EditWishRoute>().wishId, navController) { wishId ->
                WishEditorScreen(
                    viewModel = hiltViewModel<WishEditorViewModel, WishEditorViewModel.Factory> { factory ->
                        factory.create(WishEditorTarget.Existing(wishId))
                    },
                    onClose = navController.popBackStackUnlessPaused(),
                )
            }
        }
        composable<WishDetailRoute> { entry ->
            WithUuid(entry.toRoute<WishDetailRoute>().wishId, navController) { wishId ->
                WishDetailScreen(
                    viewModel = hiltViewModel<WishDetailViewModel, WishDetailViewModel.Factory> { factory ->
                        factory.create(wishId)
                    },
                    onBack = navController.popBackStackUnlessPaused(),
                    onEditWish = { id -> navController.navigate(EditWishRoute(id.toString())) },
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

/** The existing screen of an item that needs attention: its detail screen, or the editor for an occasion. */
internal fun routeOf(item: SyncItem): Any = when (item.type) {
    SyncItemType.PERSON -> PersonDetailRoute(item.id.toString())
    SyncItemType.OCCASION -> EditOccasionRoute(item.id.toString())
    SyncItemType.GIFT_IDEA -> GiftIdeaDetailRoute(item.id.toString())
    SyncItemType.GIFT -> GiftDetailRoute(item.id.toString())
    SyncItemType.WISHLIST -> WishlistDetailRoute(item.id.toString())
    SyncItemType.WISH -> WishDetailRoute(item.id.toString())
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
