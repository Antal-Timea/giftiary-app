package app.giftiary.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import java.util.UUID

/** Removes a deleted account from this device. Normal deletes are synced tombstones, never this. */
@Dao
abstract class AccountDataDao {

    /**
     * Every row of [owner], in one transaction. Rows of other accounts stay, and so do legacy
     * queue rows without an owner. Children go before what they reference: the foreign keys are
     * checked at once.
     */
    @Transaction
    open suspend fun purge(owner: UUID) {
        deleteGifts(owner)
        deleteGiftIdeas(owner)
        deleteOccasions(owner)
        deleteWishes(owner)
        deleteWishlists(owner)
        deletePeople(owner)
        deleteSyncOperations(owner)
        deleteSyncConflicts(owner)
    }

    @Query("DELETE FROM gift WHERE ownerUserId = :owner")
    protected abstract suspend fun deleteGifts(owner: UUID)

    @Query("DELETE FROM gift_idea WHERE ownerUserId = :owner")
    protected abstract suspend fun deleteGiftIdeas(owner: UUID)

    @Query("DELETE FROM occasion WHERE ownerUserId = :owner")
    protected abstract suspend fun deleteOccasions(owner: UUID)

    @Query("DELETE FROM wish WHERE ownerUserId = :owner")
    protected abstract suspend fun deleteWishes(owner: UUID)

    @Query("DELETE FROM wishlist WHERE ownerUserId = :owner")
    protected abstract suspend fun deleteWishlists(owner: UUID)

    @Query("DELETE FROM person WHERE ownerUserId = :owner")
    protected abstract suspend fun deletePeople(owner: UUID)

    @Query("DELETE FROM sync_operation WHERE ownerUserId = :owner")
    protected abstract suspend fun deleteSyncOperations(owner: UUID)

    @Query("DELETE FROM sync_conflict WHERE ownerUserId = :owner")
    protected abstract suspend fun deleteSyncConflicts(owner: UUID)
}
