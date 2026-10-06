package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperationEntity
import java.util.UUID

/**
 * Plain queue persistence. Deciding how a new change combines with a pending operation is
 * [app.gifty.data.local.SyncOperationQueue]'s job.
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

    @Query("SELECT * FROM sync_operation WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun findForEntity(entityType: SyncEntityType, entityId: UUID): SyncOperationEntity?

    /** One owner's pending operations, oldest first. Operations without an owner never match. */
    @Query("SELECT * FROM sync_operation WHERE ownerUserId = :ownerUserId ORDER BY createdAt, id LIMIT :limit")
    suspend fun getPending(ownerUserId: UUID, limit: Int): List<SyncOperationEntity>
}
