package app.giftiary.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.giftiary.data.local.entity.SyncConflictEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

@Dao
interface SyncConflictDao {

    @Insert
    suspend fun insert(conflict: SyncConflictEntity)

    /** One owner's conflicts the user has not looked at yet, newest first. */
    @Query("SELECT * FROM sync_conflict WHERE ownerUserId = :ownerUserId AND seenAt IS NULL ORDER BY createdAt DESC, id")
    fun observeUnseen(ownerUserId: UUID): Flow<List<SyncConflictEntity>>

    /** [observeUnseen] read once, e.g. inside a transaction that reads other tables with it. */
    @Query("SELECT * FROM sync_conflict WHERE ownerUserId = :ownerUserId AND seenAt IS NULL ORDER BY createdAt DESC, id")
    suspend fun getUnseen(ownerUserId: UUID): List<SyncConflictEntity>

    /** Returns 0 if the conflict is missing, already seen or not owned. */
    @Query("UPDATE sync_conflict SET seenAt = :seenAt WHERE id = :id AND ownerUserId = :ownerUserId AND seenAt IS NULL")
    suspend fun markSeen(id: UUID, ownerUserId: UUID, seenAt: Instant): Int
}
