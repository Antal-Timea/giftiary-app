package app.giftiary.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.sharing.PendingJoinViewModel
import java.util.UUID

@Composable
fun GiftiaryNavHost(accountEmail: String?, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
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
    // Outside the NavHost, so the rail and the bar stay put while destinations change beside them.
    val backStackEntry by navController.currentBackStackEntryAsState()
    var quickAddOpen by rememberSaveable { mutableStateOf(false) }
    AdaptiveNavigationFrame(
        selected = backStackEntry?.destination?.section(),
        onSelect = navController::navigateToSection,
        modifier = modifier,
        showBar = backStackEntry?.destination?.showsNavigationBar() == true,
        onAdd = { quickAddOpen = true },
    ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
        ) {
            homeDestinations(navController, accountEmail, onSignOut)
            peopleDestinations(navController)
            occasionDestinations(navController)
            giftDestinations(navController)
            wishlistDestinations(navController)
            sharingDestinations(navController)
        }
    }
    if (quickAddOpen) {
        QuickAddSheet(
            onDismiss = { quickAddOpen = false },
            onAction = { action ->
                quickAddOpen = false
                navController.openQuickAdd(action)
            },
        )
    }
}

/**
 * Settings' back circle. Opened from Profile it leads back there, beside the rail too; opened from
 * the rail it is a section of its own, with nothing to go back to.
 */
@Composable
internal fun NavHostController.settingsBack(entry: NavBackStackEntry): (() -> Unit)? {
    // Read once, while Settings is on top: what lies beneath it does not change.
    val fromProfile = remember(entry) { previousBackStackEntry?.destination?.hasRoute<ProfileRoute>() == true }
    val back = popBackStackUnlessPaused()
    return back.takeIf { fromProfile || !LocalWidthClass.current.hasNavigationRail }
}

/** Route ids are strings; a malformed one closes the destination instead of crashing. */
@Composable
internal fun WithUuid(raw: String, navController: NavHostController, content: @Composable (UUID) -> Unit) {
    val id = raw.toUuidOrNull()
    if (id != null) {
        content(id)
    } else {
        LaunchedEffect(raw) { navController.popBackStack() }
    }
}

// Ignores repeated taps while the screen is already leaving, which would otherwise pop twice.
@Composable
internal fun NavHostController.popBackStackUnlessPaused(): () -> Unit = dropUnlessResumed { popBackStack() }

/**
 * Leaves an editor whose entry was just deleted, and the deleted entry's page [T] beneath it, so
 * Back never lands on something gone. A page beside a list instead clears itself.
 */
internal inline fun <reified T : Any> NavHostController.leaveDeletedEditor() {
    popBackStack()
    if (currentBackStackEntry?.destination?.hasRoute<T>() == true) popBackStack()
}
