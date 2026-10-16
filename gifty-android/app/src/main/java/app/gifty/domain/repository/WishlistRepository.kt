package app.gifty.domain.repository

import app.gifty.domain.model.Money
import app.gifty.domain.model.Wish
import app.gifty.domain.model.Wishlist
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * The current account's wishlists and their wishes, stored on the device first; every write
 * succeeds locally regardless of network availability. Inputs are expected to be validated
 * already.
 *
 * A wishlist is either the user's own or kept for one of their people, fixed when it is created.
 * A wish stays on the wishlist it was added to. Deleting a wishlist deletes its wishes with it.
 */
interface WishlistRepository {

    /** Active wishlists, by name. */
    fun observeWishlists(): Flow<List<Wishlist>>

    /** The active wishes of wishlist [wishlistId], oldest first; empty if it is not the account's. */
    fun observeWishes(wishlistId: UUID): Flow<List<Wish>>

    /** Null if the wishlist does not exist, is deleted or is not the account's. */
    suspend fun getWishlist(id: UUID): Wishlist?

    /** Null if the wish does not exist, is deleted or is not the account's. */
    suspend fun getWish(id: UUID): Wish?

    /** @throws PersonNotFoundException if [personId] is not one of the account's active people. */
    suspend fun addWishlist(personId: UUID?, name: String): Wishlist

    /** @throws WishlistNotFoundException if the wishlist does not exist or has been deleted. */
    suspend fun renameWishlist(id: UUID, name: String)

    /** Deletes the wishlist and its wishes. Deleting one that is already gone is a no-op. */
    suspend fun deleteWishlist(id: UUID)

    /** @throws WishlistNotFoundException if [wishlistId] is not one of the account's active wishlists. */
    suspend fun addWish(wishlistId: UUID, details: WishDetails): Wish

    /**
     * @throws WishNotFoundException if the wish does not exist or has been deleted.
     * @throws WishlistNotFoundException if its wishlist has been deleted.
     */
    suspend fun updateWish(id: UUID, details: WishDetails)

    /** Deleting a wish that is already gone is a no-op. */
    suspend fun deleteWish(id: UUID)
}

/** The user-editable part of a [Wish]. Photos are not part of it yet. */
data class WishDetails(
    val name: String,
    val note: String?,
    val estimatedPrice: Money?,
    val productUrl: String?,
)

class WishlistNotFoundException(id: UUID) : Exception("Wishlist $id does not exist")

class WishNotFoundException(id: UUID) : Exception("Wish $id does not exist")
