package app.giftiary.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperationEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * Plain queue persistence. Deciding how a new change combines with a pending operation is
 * [app.giftiary.data.local.SyncOperationQueue]'s job.
 */
@Dao
interface SyncOperationDao {

    /** Fails on a second pending operation for the same entity (unique entityType + entityId). */
    @Insert
    suspend fun insert(operation: SyncOperationEntity)

    @Update
    suspend fun update(operation: SyncOperationEntity)

    @Delete
    suspend fun delete(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operation WHERE id = :id")
    suspend fun findById(id: UUID): SyncOperationEntity?

    @Query("SELECT * FROM sync_operation WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun findForEntity(entityType: SyncEntityType, entityId: UUID): SyncOperationEntity?

    /**
     * Whether the entity has a change still on its way, counted as the sync status counts it: a
     * blocked change needs attention instead, except a legacy REMOTE_NEWER block, which the next
     * push settles.
     */
    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM sync_operation
            WHERE ownerUserId = :ownerUserId AND entityType = :entityType AND entityId = :entityId
                AND (blockedReason IS NULL OR blockedReason = 'REMOTE_NEWER')
        )
        """,
    )
    fun observeWaiting(ownerUserId: UUID, entityType: SyncEntityType, entityId: UUID): Flow<Boolean>

    /**
     * The owner's whole queue, blocked operations included, oldest first. Unbounded on purpose:
     * the runner orders by dependencies, which a window of the queue could cut apart.
     */
    @Query("SELECT * FROM sync_operation WHERE ownerUserId = :ownerUserId ORDER BY createdAt, id")
    suspend fun getAllForOwner(ownerUserId: UUID): List<SyncOperationEntity>

    /**
     * Records that the operation's current payload, [mutationId], is about to be sent. Returns 0,
     * changing nothing, if the payload has changed meanwhile, an earlier mutation is still
     * unresolved (there is only ever one mutation in flight per entity), or the mutation is
     * blocked: a blocked mutation is never sent.
     */
    @Query(
        """
        UPDATE sync_operation SET inFlightMutationId = :mutationId
        WHERE id = :id AND mutationId = :mutationId AND inFlightMutationId IS NULL AND blockedReason IS NULL
        """,
    )
    suspend fun markInFlight(id: UUID, mutationId: UUID): Int

    /**
     * Counts a write request for the in-flight mutation [mutationId]. While a mutation is in flight,
     * the attempt fields describe its sends since the last local change; 0, changing nothing, if
     * [mutationId] is no longer the one in flight.
     */
    @Query(
        """
        UPDATE sync_operation SET attemptCount = attemptCount + 1, lastAttemptAt = :attemptedAt
        WHERE id = :id AND inFlightMutationId = :mutationId
        """,
    )
    suspend fun recordAttempt(id: UUID, mutationId: UUID, attemptedAt: Instant): Int

    /** Notes why the in-flight mutation [mutationId] is still unresolved; 0 if it is no longer in flight. */
    @Query("UPDATE sync_operation SET lastErrorCode = :errorCode WHERE id = :id AND inFlightMutationId = :mutationId")
    suspend fun recordError(id: UUID, mutationId: UUID, errorCode: String): Int
}
