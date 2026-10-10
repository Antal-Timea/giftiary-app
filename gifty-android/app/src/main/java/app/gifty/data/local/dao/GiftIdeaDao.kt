package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.gifty.data.local.entity.GiftIdeaEntity
import app.gifty.data.local.entity.GiftIdeaWithReferences
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * User-facing reads and deletes are scoped by owner. "Active" ideas are neither deleted nor
 * archived; archived ideas have been given and only remain as the source of their gift.
 */
@Dao
interface GiftIdeaDao {

    @Transaction
    @Query(
        """
        SELECT * FROM gift_idea
        WHERE ownerUserId = :ownerUserId AND deletedAt IS NULL AND archivedAt IS NULL
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
        ORDER BY updatedAt DESC, createdAt DESC, id
        """,
    )
    fun observeActiveForPerson(ownerUserId: UUID, personId: UUID): Flow<List<GiftIdeaWithReferences>>

    @Transaction
    @Query(
        """
        SELECT * FROM gift_idea
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL AND archivedAt IS NULL
        """,
    )
    fun observeActiveById(id: UUID, ownerUserId: UUID): Flow<GiftIdeaWithReferences?>

    @Query(
        """
        SELECT * FROM gift_idea
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL AND archivedAt IS NULL
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
