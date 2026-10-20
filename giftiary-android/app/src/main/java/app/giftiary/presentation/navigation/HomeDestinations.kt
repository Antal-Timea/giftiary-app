package app.giftiary.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.home.ComingUpViewModel
import app.giftiary.presentation.home.HomeScreen
import app.giftiary.presentation.home.ReminderCardViewModel
import app.giftiary.presentation.home.rememberReminderCardActions
import app.giftiary.presentation.settings.SettingsScreen
import app.giftiary.presentation.sync.SyncAttentionScreen
import app.giftiary.presentation.sync.SyncStatusViewModel

/** Home, the sync attention list and Settings. */
internal fun NavGraphBuilder.homeDestinations(navController: NavHostController, onSignOut: () -> Unit) {
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
            showSectionButtons = !LocalWidthClass.current.hasNavigationRail,
        )
    }
    composable<SyncAttentionRoute> {
        SyncAttentionScreen(
            onBack = navController.popBackStackUnlessPaused(),
            onOpen = { item -> navController.navigate(routeOf(item)) },
        )
    }
    composable<SettingsRoute> {
        SettingsScreen(
            viewModel = hiltViewModel(),
            reminders = hiltViewModel(),
            onBack = navController.sectionRootBack(),
            onSignOut = onSignOut,
        )
    }
}
