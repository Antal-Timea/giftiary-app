package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.gifty.data.local.entity.OccasionEntity
import app.gifty.data.local.entity.OccasionWithPerson
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * User-facing reads and deletes are scoped by owner so one user's occasions can never be read or
 * changed on behalf of another.
 */
@Dao
interface OccasionDao {

    @Query(
        """
        SELECT * FROM occasion
        WHERE ownerUserId = :ownerUserId AND personId = :personId AND deletedAt IS NULL
        ORDER BY month, day, id
        """,
    )
    fun observeActiveForPerson(ownerUserId: UUID, personId: UUID): Flow<List<OccasionEntity>>

    /** The owner's active occasions, each either without a person or with an active one of the same owner. */
    @Transaction
    @Query(ACTIVE_WITH_PERSON)
    fun observeActiveWithPerson(ownerUserId: UUID): Flow<List<OccasionWithPerson>>

    /** The same rows as [observeActiveWithPerson], read once. */
    @Transaction
    @Query(ACTIVE_WITH_PERSON)
    suspend fun findActiveWithPerson(ownerUserId: UUID): List<OccasionWithPerson>

    @Query(
        """
        SELECT * FROM occasion
        WHERE ownerUserId = :ownerUserId AND personId = :personId AND deletedAt IS NULL
        """,
    )
    suspend fun findActiveForPerson(ownerUserId: UUID, personId: UUID): List<OccasionEntity>

    @Query("SELECT * FROM occasion WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL")
    suspend fun findActiveById(id: UUID, ownerUserId: UUID): OccasionEntity?

    /** Includes soft-deleted rows; only for checking references gifts already have. */
    @Query("SELECT * FROM occasion WHERE id = :id AND ownerUserId = :ownerUserId")
    suspend fun findById(id: UUID, ownerUserId: UUID): OccasionEntity?

    /** Whichever account's row has [id]; for sync, which must tell another account's row from none. */
    @Query("SELECT * FROM occasion WHERE id = :id")
    suspend fun findByIdForAnyOwner(id: UUID): OccasionEntity?

    /** Fails if the id already exists, which for a locally created occasion indicates a bug. */
    @Insert
    suspend fun insert(occasion: OccasionEntity)

    @Update
    suspend fun update(occasion: OccasionEntity): Int

    /** Returns the number of updated rows, 0 if the occasion is missing, already deleted or not owned. */
    @Query(
        """
        UPDATE occasion SET deletedAt = :deletedAt, updatedAt = :deletedAt
        WHERE id = :id AND ownerUserId = :ownerUserId AND deletedAt IS NULL
        """,
    )
    suspend fun softDelete(id: UUID, ownerUserId: UUID, deletedAt: Instant): Int

    /**
     * Records that the backend committed this row at [version], the version after the local one.
     * Returns 0, changing nothing, if the row is missing, not owned or not at the version before.
     * Only sync metadata changes: content, tombstone and local timestamps stay as they are.
     */
    @Query(
        """
        UPDATE occasion SET version = :version, remoteState = 'SYNCED'
        WHERE id = :id AND ownerUserId = :ownerUserId AND version = :version - 1
        """,
    )
    suspend fun adoptRemoteVersion(id: UUID, ownerUserId: UUID, version: Long): Int
}

// Shared so that the live and the one-off read can never disagree about which rows count.
private const val ACTIVE_WITH_PERSON = """
    SELECT occasion.* FROM occasion
    LEFT JOIN person ON person.id = occasion.personId
    WHERE occasion.ownerUserId = :ownerUserId AND occasion.deletedAt IS NULL
        AND (occasion.personId IS NULL OR (person.ownerUserId = :ownerUserId AND person.deletedAt IS NULL))
    ORDER BY occasion.month, occasion.day, occasion.id
"""
