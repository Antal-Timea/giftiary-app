package app.giftiary.domain.repository

import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.ReservationState
import app.giftiary.domain.model.SharedWishlist
import app.giftiary.domain.model.SharedWishlistSummary
import app.giftiary.domain.model.WishlistAccess
import java.util.UUID

/**
 * Sharing of wishlists, online only. Shared data is server-owned and is never stored on the device
 * or synced: every call asks the server, as the signed-in account, and fails with a
 * [SharingException] when that is not possible.
 */
interface WishlistSharingRepository {

    // Owner side, for an own wishlist the server already has.

    suspend fun createInvite(wishlistId: UUID): CreatedInvite

    suspend fun revokeInvite(inviteId: UUID)

    suspend fun access(wishlistId: UUID): WishlistAccess

    suspend fun removeMember(wishlistId: UUID, memberUserId: UUID)

    // Guest side.

    /** Joins the wishlist the invite is for and returns its id; joining again is not an error. */
    suspend fun redeem(token: InviteToken): UUID

    suspend fun sharedWishlists(): List<SharedWishlistSummary>

    suspend fun sharedWishlist(wishlistId: UUID): SharedWishlist

    /** Ends the user's own membership; leaving a list one is no longer a member of is not an error. */
    suspend fun leave(wishlistId: UUID)

    /**
     * Asks the server to reserve the wish for the user and returns what it decided: reserved by
     * the user, or already reserved by someone else (never by whom).
     */
    suspend fun reserve(wishId: UUID): ReservationState

    /** Releases the user's own reservation and returns the wish's state as the server now sees it. */
    suspend fun release(wishId: UUID): ReservationState
}

enum class SharingFailure {
    /**
     * The server refused without saying why: no such invite, list or member, an expired or revoked
     * invite, no access, or a full list all look the same, so nothing is learnt about which.
     */
    UNAVAILABLE,

    /** An own wishlist already has the most open invites allowed. */
    INVITE_LIMIT,

    /** No answer; trying again may work. */
    NETWORK,

    /** Anything else, e.g. an expired session or an unreadable answer; trying again may work. */
    SERVER,
}

class SharingException(val failure: SharingFailure, cause: Throwable? = null) : Exception(failure.name, cause)
