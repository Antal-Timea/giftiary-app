package app.gifty.data.local

import androidx.room.withTransaction
import app.gifty.data.local.dao.LegacyOwnershipDao
import java.util.UUID
import javax.inject.Inject

/**
 * Hands every row owned by the legacy device id over to an account, in one transaction.
 *
 * Before accounts existed, everything on the device belonged to one locally generated id. The
 * first account that signs in takes over that data. Only ownership changes: ids, versions,
 * remote states, timestamps, tombstones and archive markers stay exactly as they are, and no sync
 * operations are added, because a claim is not a user edit. Pending operations simply move with
 * their rows.
 *
 * Unowned sync operations are claimed too. Owners were added to the queue in schema v2, and a v1
 * database had exactly one owner, the legacy id, so an operation without an owner can only be the
 * legacy owner's. That holds only for this legacy claim; nothing else may assign unowned
 * operations.
 *
 * Safe to repeat: once claimed, nothing is owned by the legacy id or unowned any more, so a later
 * claim, for the same or another account, moves nothing.
 */
class LegacyOwnershipClaim @Inject constructor(
    private val database: GiftyDatabase,
    private val dao: LegacyOwnershipDao,
) {

    suspend fun claimLegacyData(legacyOwnerId: UUID, accountOwnerId: UUID): LegacyClaimResult {
        require(legacyOwnerId != accountOwnerId) { "The legacy owner and the account must differ" }
        return database.withTransaction {
            LegacyClaimResult(
                peopleClaimed = dao.claimPeople(legacyOwnerId, accountOwnerId),
                occasionsClaimed = dao.claimOccasions(legacyOwnerId, accountOwnerId),
                giftIdeasClaimed = dao.claimGiftIdeas(legacyOwnerId, accountOwnerId),
                giftsClaimed = dao.claimGifts(legacyOwnerId, accountOwnerId),
                wishlistsClaimed = dao.claimWishlists(legacyOwnerId, accountOwnerId),
                syncOperationsClaimed = dao.claimSyncOperations(legacyOwnerId, accountOwnerId),
            )
        }
    }
}

/** How many rows of each table moved to the account; all zero when nothing was left to claim. */
data class LegacyClaimResult(
    val peopleClaimed: Int,
    val occasionsClaimed: Int,
    val giftIdeasClaimed: Int,
    val giftsClaimed: Int,
    val wishlistsClaimed: Int,
    val syncOperationsClaimed: Int,
)
