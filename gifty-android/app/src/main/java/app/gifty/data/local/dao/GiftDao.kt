package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.gifty.data.local.entity.GiftEntity
import app.gifty.data.local.entity.GiftWithReferences
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * User-facing reads and deletes are scoped by owner so one user's gifts can never be read or
 * changed on behalf of another. Newest gifts come first; ties are broken deterministically.
 */
@Dao
interface GiftDao {

    @Transaction
    @Query(
        """
        SELECT * FROM gift
        WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL
        ORDER BY givenAt DESC, updatedAt DESC, id
        """,
    )
    fun observeActive(ownerUserId: UUID): Flow<List<GiftWithReferences>>

    @Transaction
    @Query(
        """
        SELECT * FROM gift
        WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL
            AND (giverPersonId = :personId OR recipientPersonId = :personId)
        ORDER BY givenAt DESC, updatedAt DESC, id
        """,
    )
    fun observeActiveForPerson(ownerUserId: UUID, personId: UUID): Flow<List<GiftWithReferences>>

    @Transaction
    @Query("SELECT * FROM gift WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    fun observeActiveById(id: UUID, ownerUserId: UUID): Flow<GiftWithReferences?>

    @Query("SELECT * FROM gift WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun findActiveById(id: UUID, ownerUserId: UUID): GiftEntity?

    /** Fails if the id already exists, which for a locally created gift indicates a bug. */
    @Insert
    suspend fun insert(gift: GiftEntity)

    @Update
    suspend fun update(gift: GiftEntity): Int

    /** Returns the number of updated rows, 0 if the gift is missing, already deleted or not owned. */
    @Query(
        """
        UPDATE gift SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(id: UUID, ownerUserId: UUID, deletedAt: Instant): Int

    /** Deleted rows included, for sync bookkeeping. */
    @Query("SELECT * FROM gift WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun findById(id: UUID, ownerUserId: UUID): GiftEntity?

    /**
     * Records that the backend committed this row at [version], the version after the local one.
     * Returns 0, changing nothing, if the row is missing, not owned or not at the version before.
     * Only sync metadata changes: content, tombstone and local timestamps stay as they are.
     */
    @Query(
        """
        UPDATE gift SET version = :version, remoteState = 'SYNCED'
        WHERE id = :id AND ownerUserId = :ownerUserId AND version = :version - 1
        """,
    )
    suspend fun adoptRemoteVersion(id: UUID, ownerUserId: UUID, version: Long): Int
}
