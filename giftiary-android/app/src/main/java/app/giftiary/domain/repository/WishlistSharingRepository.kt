package app.giftiary.domain.repository

import app.giftiary.domain.model.CreatedInvite
import app.giftiary.domain.model.EmailInvite
import app.giftiary.domain.model.InviteToken
import app.giftiary.domain.model.RecipientInvite
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

    /**
     * Invites [email] (already normalized) to an own wishlist. The answer is the same whether the
     * address has an account or not; for an address already pending, it is that invite.
     */
    suspend fun createEmailInvite(wishlistId: UUID, email: String): EmailInvite

    suspend fun emailInvites(wishlistId: UUID): List<EmailInvite>

    /** Withdraws a pending email invite, or clears a not accepted one from the owner's list. */
    suspend fun revokeEmailInvite(inviteId: UUID)

    /**
     * The reserved wishes of an own wishlist kept for a person, never who reserved them. The server
     * refuses an own wishlist of the user's own, so its owner is never told.
     */
    suspend fun reservedWishes(wishlistId: UUID): Set<UUID>

    // Guest side.

    /** Joins the wishlist the invite is for and returns its id; joining again is not an error. */
    suspend fun redeem(token: InviteToken): UUID

    suspend fun sharedWishlists(): List<SharedWishlistSummary>

    /** Open invitations to the user's confirmed address; none without one. */
    suspend fun myInvites(): List<RecipientInvite>

    /** Joins the invitation's wishlist and returns its id. */
    suspend fun acceptInvite(inviteId: UUID): UUID

    suspend fun declineInvite(inviteId: UUID)

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

    /**
     * An own wishlist already has the most open invites allowed; for email invites also the most
     * an owner may send in a day.
     */
    INVITE_LIMIT,

    /** The server refused the input, e.g. an invalid email address or the owner's own. */
    INVALID_INPUT,

    /** The email invite is for someone who already has access to the wishlist. */
    ALREADY_MEMBER,

    /** No answer; trying again may work. */
    NETWORK,

    /** Anything else, e.g. an expired session or an unreadable answer; trying again may work. */
    SERVER,
}

class SharingException(val failure: SharingFailure, cause: Throwable? = null) : Exception(failure.name, cause)
