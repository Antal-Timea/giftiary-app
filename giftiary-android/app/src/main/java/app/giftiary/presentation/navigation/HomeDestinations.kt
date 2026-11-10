package app.giftiary.presentation.navigation

import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.giftiary.presentation.home.ComingUpViewModel
import app.giftiary.presentation.home.HomeAvatarViewModel
import app.giftiary.presentation.home.HomeScreen
import app.giftiary.presentation.home.RecentGiftsViewModel
import app.giftiary.presentation.home.ReminderCardViewModel
import app.giftiary.presentation.home.rememberReminderCardActions
import app.giftiary.presentation.profile.EditProfileScreen
import app.giftiary.presentation.profile.ProfileScreen
import app.giftiary.presentation.profile.ownMonogramName
import app.giftiary.presentation.settings.SettingsScreen
import app.giftiary.presentation.sync.SyncAttentionScreen
import app.giftiary.presentation.sync.SyncStatusViewModel

/** Home, the sync attention list, and the user's own Profile and Settings, opened from Home. */
internal fun NavGraphBuilder.homeDestinations(navController: NavHostController, accountEmail: String?, onSignOut: () -> Unit) {
    composable<HomeRoute> {
        val syncStatus by hiltViewModel<SyncStatusViewModel>().content.collectAsStateWithLifecycle()
        val comingUp by hiltViewModel<ComingUpViewModel>().content.collectAsStateWithLifecycle()
        val reminders = hiltViewModel<ReminderCardViewModel>()
        val reminderCard by reminders.state.collectAsStateWithLifecycle()
        val recentGifts by hiltViewModel<RecentGiftsViewModel>().content.collectAsStateWithLifecycle()
        val homeAvatar = hiltViewModel<HomeAvatarViewModel>()
        val accountAvatar by homeAvatar.avatar.collectAsStateWithLifecycle()
        val displayName by homeAvatar.displayName.collectAsStateWithLifecycle()
        LifecycleResumeEffect(homeAvatar) {
            homeAvatar.onResume()
            onPauseOrDispose {}
        }
        HomeScreen(
            syncStatus = syncStatus,
            comingUp = comingUp,
            reminderCard = reminderCard,
            reminderActions = rememberReminderCardActions(reminders),
            onOpenComingUp = { occasionId -> navController.navigate(OccasionPlanRoute(occasionId.toString())) },
            recentGifts = recentGifts,
            onOpenGift = { giftId -> navController.navigate(GiftDetailRoute(giftId.toString())) },
            onOpenAllGifts = dropUnlessResumed { navController.navigateToSection(AppSection.GIFTS) },
            onOpenOwnOccasions = dropUnlessResumed { navController.navigate(OwnOccasionsRoute) },
            onOpenSyncAttention = dropUnlessResumed { navController.navigate(SyncAttentionRoute) },
            accountName = ownMonogramName(displayName, accountEmail),
            accountAvatar = accountAvatar,
            onOpenProfile = dropUnlessResumed { navController.navigate(ProfileRoute) },
        )
    }
    composable<SyncAttentionRoute> {
        SyncAttentionScreen(
            onBack = navController.popBackStackUnlessPaused(),
            onOpen = { item -> navController.navigate(routeOf(item)) },
        )
    }
    composable<ProfileRoute> {
        ProfileScreen(
            viewModel = hiltViewModel(),
            onBack = navController.popBackStackUnlessPaused(),
            onEditProfile = dropUnlessResumed { navController.navigate(EditProfileRoute) },
            onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
            onSignOut = onSignOut,
        )
    }
    composable<EditProfileRoute> {
        EditProfileScreen(viewModel = hiltViewModel(), onBack = navController.popBackStackUnlessPaused())
    }
    composable<SettingsRoute> { entry ->
        SettingsScreen(
            viewModel = hiltViewModel(),
            reminders = hiltViewModel(),
            onBack = navController.settingsBack(entry),
            onSignOut = onSignOut,
        )
    }
}
