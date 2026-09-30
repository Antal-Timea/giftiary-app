package app.gifty.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperationEntity
import java.util.UUID

@Dao
interface SyncOperationDao {

    /**
     * Stores [operation] as the only pending operation for its entity, replacing any existing one
     * through the unique (entityType, entityId) index. Deciding how a new change combines with a
     * pending one (e.g. an update after an unsynced create) is up to the caller.
     *
     * Room's `@Upsert` is not used on purpose: it resolves conflicts by primary key only and would
     * silently drop an operation that collides on the unique index with a different id.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operation WHERE entityType = :entityType AND entityId = :entityId")
    suspend fun findForEntity(entityType: SyncEntityType, entityId: UUID): SyncOperationEntity?

    @Query("SELECT * FROM sync_operation ORDER BY createdAt, id LIMIT :limit")
    suspend fun getPending(limit: Int): List<SyncOperationEntity>
}
