package app.giftiary.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.SyncItem
import app.giftiary.domain.model.SyncItemType
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
import app.giftiary.presentation.home.ComingUpViewModel
import app.giftiary.presentation.home.HomeScreen
import app.giftiary.presentation.home.ReminderCardViewModel
import app.giftiary.presentation.home.rememberReminderCardActions
import app.giftiary.presentation.occasions.OccasionEditorScreen
import app.giftiary.presentation.occasions.OccasionEditorTarget
import app.giftiary.presentation.occasions.OccasionEditorViewModel
import app.giftiary.presentation.occasions.OccasionPlanScreen
import app.giftiary.presentation.occasions.OccasionPlanViewModel
import app.giftiary.presentation.occasions.OwnOccasionsScreen
import app.giftiary.presentation.people.PeopleScreen
import app.giftiary.presentation.people.PersonDetailScreen
import app.giftiary.presentation.people.PersonDetailViewModel
import app.giftiary.presentation.people.PersonEditorScreen
import app.giftiary.presentation.people.PersonEditorViewModel
import app.giftiary.presentation.settings.SettingsScreen
import app.giftiary.presentation.sharing.JoinWishlistScreen
import app.giftiary.presentation.sharing.PendingJoinViewModel
import app.giftiary.presentation.sharing.SharedWishlistScreen
import app.giftiary.presentation.sharing.SharedWishlistViewModel
import app.giftiary.presentation.sharing.SharedWishlistsScreen
import app.giftiary.presentation.sharing.WishlistAccessScreen
import app.giftiary.presentation.sharing.WishlistAccessViewModel
import app.giftiary.presentation.sync.SyncAttentionScreen
import app.giftiary.presentation.sync.SyncStatusViewModel
import app.giftiary.presentation.wishlists.WishDetailScreen
import app.giftiary.presentation.wishlists.WishDetailViewModel
import app.giftiary.presentation.wishlists.WishEditorScreen
import app.giftiary.presentation.wishlists.WishEditorTarget
import app.giftiary.presentation.wishlists.WishEditorViewModel
import app.giftiary.presentation.wishlists.WishlistDetailScreen
import app.giftiary.presentation.wishlists.WishlistDetailViewModel
import app.giftiary.presentation.wishlists.WishlistEditorScreen
import app.giftiary.presentation.wishlists.WishlistEditorTarget
import app.giftiary.presentation.wishlists.WishlistEditorViewModel
import app.giftiary.presentation.wishlists.WishlistsScreen
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data object HomeRoute

@Serializable
data object PeopleRoute

@Serializable
data object SyncAttentionRoute

@Serializable
data object SettingsRoute

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

@Serializable
data class WishlistAccessRoute(val wishlistId: String)

@Serializable
data object SharedWishlistsRoute

@Serializable
data class SharedWishlistRoute(val wishlistId: String)

/** Takes an opened join link from memory when it opens; a token is never a route argument. */
@Serializable
data object JoinWishlistRoute

@Composable
fun GiftiaryNavHost(onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val joinPending by hiltViewModel<PendingJoinViewModel>().isPending.collectAsStateWithLifecycle()
    // A join link opened now, or while signed out, opens the join screen; a fresh one each time,
    // since the screen takes the link when it opens.
    LaunchedEffect(joinPending) {
        if (joinPending) {
            navController.navigate(JoinWishlistRoute) {
                popUpTo<JoinWishlistRoute> { inclusive = true }
            }
        }
    }
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
            HomeScreen(
                syncStatus = syncStatus,
                comingUp = comingUp,
                reminderCard = reminderCard,
                reminderActions = rememberReminderCardActions(reminders),
                onOpenComingUp = { occasionId -> navController.navigate(occasionPlanRoute(occasionId)) },
                onOpenGifts = dropUnlessResumed { navController.navigate(GiftHistoryRoute) },
                onOpenPeople = dropUnlessResumed { navController.navigate(PeopleRoute) },
                onOpenWishlists = dropUnlessResumed { navController.navigate(WishlistsRoute) },
                onOpenOwnOccasions = dropUnlessResumed { navController.navigate(OwnOccasionsRoute) },
                onOpenSyncAttention = dropUnlessResumed { navController.navigate(SyncAttentionRoute) },
                onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = hiltViewModel(),
                reminders = hiltViewModel(),
                onBack = navController.popBackStackUnlessPaused(),
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
                onOpenShared = dropUnlessResumed { navController.navigate(SharedWishlistsRoute) },
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
                    onShare = { id -> navController.navigate(WishlistAccessRoute(id.toString())) },
                )
            }
        }
        composable<WishlistAccessRoute> { entry ->
            WithUuid(entry.toRoute<WishlistAccessRoute>().wishlistId, navController) { wishlistId ->
                WishlistAccessScreen(
                    viewModel = hiltViewModel<WishlistAccessViewModel, WishlistAccessViewModel.Factory> { factory ->
                        factory.create(wishlistId)
                    },
                    onBack = navController.popBackStackUnlessPaused(),
                    onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
                )
            }
        }
        composable<SharedWishlistsRoute> {
            SharedWishlistsScreen(
                viewModel = hiltViewModel(),
                onBack = navController.popBackStackUnlessPaused(),
                onJoin = dropUnlessResumed { navController.navigate(JoinWishlistRoute) },
                onOpenWishlist = { id -> navController.navigate(SharedWishlistRoute(id.toString())) },
            )
        }
        composable<SharedWishlistRoute> { entry ->
            WithUuid(entry.toRoute<SharedWishlistRoute>().wishlistId, navController) { wishlistId ->
                SharedWishlistScreen(
                    viewModel = hiltViewModel<SharedWishlistViewModel, SharedWishlistViewModel.Factory> { factory ->
                        factory.create(wishlistId)
                    },
                    onBack = navController.popBackStackUnlessPaused(),
                )
            }
        }
        composable<JoinWishlistRoute> {
            JoinWishlistScreen(
                viewModel = hiltViewModel(),
                onBack = navController.popBackStackUnlessPaused(),
                onJoined = { wishlistId ->
                    navController.navigate(joinedRoute(wishlistId)) {
                        popUpTo<JoinWishlistRoute> { inclusive = true }
                    }
                },
            )
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

/** A joined list opens in place of the join screen, so back leads to where the join started. */
internal fun joinedRoute(wishlistId: UUID) = SharedWishlistRoute(wishlistId.toString())

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
