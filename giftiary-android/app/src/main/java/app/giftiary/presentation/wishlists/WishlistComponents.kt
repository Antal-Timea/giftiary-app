package app.giftiary.presentation.wishlists

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.giftiary.R

/** Who a wishlist is for, as shown to the user. */
sealed interface WishlistOwner {
    data object Me : WishlistOwner
    data class Person(val name: String) : WishlistOwner

    /** The person was deleted; the wishlist stays, but there is no one to open. */
    data object PersonRemoved : WishlistOwner
}

@Composable
internal fun wishlistOwnerText(owner: WishlistOwner): String = when (owner) {
    WishlistOwner.Me -> stringResource(R.string.wishlist_for_me)
    is WishlistOwner.Person -> stringResource(R.string.wishlist_for_person, owner.name)
    WishlistOwner.PersonRemoved -> stringResource(R.string.wishlist_person_removed)
}

