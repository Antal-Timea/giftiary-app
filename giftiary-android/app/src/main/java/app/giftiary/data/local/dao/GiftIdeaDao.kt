package app.giftiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.giftiary.data.local.entity.GiftIdeaEntity
import app.giftiary.data.local.entity.GiftIdeaWithReferences
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * User-facing reads and deletes are scoped by owner. "Active" ideas are neither deleted nor given.
 * An idea is given once it is archived, or once a gift of its owner, even a deleted one, was made
 * from it: a sync can reopen the idea while its gift stays, and the backend reserves the idea for
 * that gift either way. Given ideas only remain as the source of their gift.
 */
@Dao
interface GiftIdeaDao {

    @Transaction
    @Query(
        """
        SELECT * FROM gift_idea
        WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL AND archivedAt IS NULL
            AND NOT EXISTS (SELECT 1 FROM gift WHERE gift.sourceGiftIdeaId = gift_idea.id AND gift.ownerUserId = gift_idea.ownerUserId)
        ORDER BY updatedAt DESC, createdAt DESC, id
        """,
    )
    fun observeActive(ownerUserId: UUID): Flow<List<GiftIdeaWithReferences>>

    @Transaction
    @Query(
        """
        SELECT * FROM gift_idea
        WHERE ownerUserId = :ownerUserId AND recipientPersonId = :personId
            AND deletedAt IS NULL AND archivedAt IS NULL
            AND NOT EXISTS (SELECT 1 FROM gift WHERE gift.sourceGiftIdeaId = gift_idea.id AND gift.ownerUserId = gift_idea.ownerUserId)
        ORDER BY updatedAt DESC, createdAt DESC, id
        """,
    )
    fun observeActiveForPerson(ownerUserId: UUID, personId: UUID): Flow<List<GiftIdeaWithReferences>>

    @Transaction
    @Query(
        """
        SELECT * FROM gift_idea
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL AND archivedAt IS NULL
            AND NOT EXISTS (SELECT 1 FROM gift WHERE gift.sourceGiftIdeaId = gift_idea.id AND gift.ownerUserId = gift_idea.ownerUserId)
        """,
    )
    fun observeActiveById(id: UUID, ownerUserId: UUID): Flow<GiftIdeaWithReferences?>

    @Query(
        """
        SELECT * FROM gift_idea
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL AND archivedAt IS NULL
            AND NOT EXISTS (SELECT 1 FROM gift WHERE gift.sourceGiftIdeaId = gift_idea.id AND gift.ownerUserId = gift_idea.ownerUserId)
        """,
    )
    suspend fun findActiveById(id: UUID, ownerUserId: UUID): GiftIdeaEntity?

    /** Includes archived ideas, so a second "mark as given" can be recognised. */
    @Query("SELECT * FROM gift_idea WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun findNotDeletedById(id: UUID, ownerUserId: UUID): GiftIdeaEntity?

    /** Fails if the id already exists, which for a locally created idea indicates a bug. */
    @Insert
    suspend fun insert(idea: GiftIdeaEntity)

    @Update
    suspend fun update(idea: GiftIdeaEntity): Int

    /** Returns the number of updated rows, 0 if the idea is missing, already deleted or not owned. */
    @Query(
        """
        UPDATE gift_idea SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(id: UUID, ownerUserId: UUID, deletedAt: Instant): Int

    /** Deleted and archived rows included, for sync bookkeeping. */
    @Query("SELECT * FROM gift_idea WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun findById(id: UUID, ownerUserId: UUID): GiftIdeaEntity?

    /** Every photo the owner's gift ideas name, archived and deleted ones included. */
    @Query("SELECT photoPath FROM gift_idea WHERE ownerUserId = :ownerUserId AND photoPath IS NOT NULL")
    suspend fun getPhotoPaths(ownerUserId: UUID): List<String>

    /** Whichever account's row has [id]; for sync, which must tell another account's row from none. */
    @Query("SELECT * FROM gift_idea WHERE id = :id")
    suspend fun findByIdForAnyOwner(id: UUID): GiftIdeaEntity?

    /**
     * Records that the backend committed this row at [version], the version after the local one.
     * Returns 0, changing nothing, if the row is missing, not owned or not at the version before.
     * Only sync metadata changes: content, tombstone and local timestamps stay as they are.
     */
    @Query(
        """
        UPDATE gift_idea SET version = :version, remoteState = 'SYNCED'
        WHERE id = :id AND ownerUserId = :ownerUserId AND version = :version - 1
        """,
    )
    suspend fun adoptRemoteVersion(id: UUID, ownerUserId: UUID, version: Long): Int
}
