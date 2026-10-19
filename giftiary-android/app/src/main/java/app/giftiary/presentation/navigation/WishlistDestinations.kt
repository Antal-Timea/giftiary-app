package app.giftiary.presentation.navigation

import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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

/** The user's wishlists and their wishes. */
internal fun NavGraphBuilder.wishlistDestinations(navController: NavHostController) {
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
}
