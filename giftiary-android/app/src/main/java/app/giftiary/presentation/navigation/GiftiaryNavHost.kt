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
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.giftiary.presentation.components.LocalWidthClass
import app.giftiary.presentation.sharing.PendingJoinViewModel
import java.util.UUID

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
    // Outside the NavHost, so the rail stays put while destinations change beside it.
    val backStackEntry by navController.currentBackStackEntryAsState()
    AdaptiveNavigationFrame(
        selected = backStackEntry?.destination?.section(),
        onSelect = navController::navigateToSection,
        modifier = modifier,
    ) {
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
        ) {
            homeDestinations(navController, onSignOut)
            peopleDestinations(navController)
            occasionDestinations(navController)
            giftDestinations(navController)
            wishlistDestinations(navController)
            sharingDestinations(navController)
        }
    }
}

/** A section's own list: its back arrow is dropped when the rail is there to leave it. */
@Composable
internal fun NavHostController.sectionRootBack(): (() -> Unit)? {
    val back = popBackStackUnlessPaused()
    return back.takeUnless { LocalWidthClass.current.hasNavigationRail }
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
