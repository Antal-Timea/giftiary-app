package app.giftiary.presentation.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import app.giftiary.presentation.sharing.JoinWishlistScreen
import app.giftiary.presentation.sharing.SharedWishlistScreen
import app.giftiary.presentation.sharing.SharedWishlistViewModel
import app.giftiary.presentation.sharing.WishlistAccessScreen
import app.giftiary.presentation.sharing.WishlistAccessViewModel

/** Sharing: an owner's access screen, a wishlist shared with the user, and joining one. */
internal fun NavGraphBuilder.sharingDestinations(navController: NavHostController) {
    composable<WishlistAccessRoute> { entry ->
        WithUuid(entry.toRoute<WishlistAccessRoute>().wishlistId, navController) { wishlistId ->
            WishlistAccessScreen(
                viewModel = hiltViewModel<WishlistAccessViewModel, WishlistAccessViewModel.Factory> { factory ->
                    factory.create(wishlistId)
                },
                onBack = navController.popBackStackUnlessPaused(),
                onEditName = dropUnlessResumed { navController.navigate(EditProfileRoute) },
            )
        }
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
            // The joined list opens in place of the join screen, so back leads to where the join started.
            onJoined = { wishlistId ->
                navController.navigate(SharedWishlistRoute(wishlistId.toString())) {
                    popUpTo<JoinWishlistRoute> { inclusive = true }
                }
            },
        )
    }
}
