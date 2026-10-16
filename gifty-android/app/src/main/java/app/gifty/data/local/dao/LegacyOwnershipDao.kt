package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import java.util.UUID

/**
 * The statements of the one-time legacy ownership claim. Only
 * [app.gifty.data.local.LegacyOwnershipClaim] calls them, all inside one transaction; this is not
 * a general way to change owners. Each statement returns the number of rows it moved.
 *
 * Only rows of the legacy owner move: rows of any account are never touched. Same-owner foreign
 * keys cascade the rest: a person's new owner moves their wishlists, and a wishlist's its wishes.
 */
@Dao
interface LegacyOwnershipDao {

    @Query("UPDATE person SET ownerUserId = :accountOwnerId WHERE ownerUserId = :legacyOwnerId")
    suspend fun claimPeople(legacyOwnerId: UUID, accountOwnerId: UUID): Int

    @Query("UPDATE occasion SET ownerUserId = :accountOwnerId WHERE ownerUserId = :legacyOwnerId")
    suspend fun claimOccasions(legacyOwnerId: UUID, accountOwnerId: UUID): Int

    @Query("UPDATE gift_idea SET ownerUserId = :accountOwnerId WHERE ownerUserId = :legacyOwnerId")
    suspend fun claimGiftIdeas(legacyOwnerId: UUID, accountOwnerId: UUID): Int

    @Query("UPDATE gift SET ownerUserId = :accountOwnerId WHERE ownerUserId = :legacyOwnerId")
    suspend fun claimGifts(legacyOwnerId: UUID, accountOwnerId: UUID): Int

    /** Counted before people are claimed, as claiming a person already moves their wishlists. */
    @Query("SELECT COUNT(*) FROM wishlist WHERE ownerUserId = :legacyOwnerId")
    suspend fun countWishlists(legacyOwnerId: UUID): Int

    /** Moves the wishlists without a person; those with one moved with their person. */
    @Query("UPDATE wishlist SET ownerUserId = :accountOwnerId WHERE ownerUserId = :legacyOwnerId")
    suspend fun claimWishlists(legacyOwnerId: UUID, accountOwnerId: UUID): Int

    /** Unowned operations count as the legacy owner's; see LegacyOwnershipClaim for why. */
    @Query(
        "UPDATE sync_operation SET ownerUserId = :accountOwnerId " +
            "WHERE ownerUserId IS NULL OR ownerUserId = :legacyOwnerId",
    )
    suspend fun claimSyncOperations(legacyOwnerId: UUID, accountOwnerId: UUID): Int
}
