package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Pending work to push one local entity to the backend. There is at most one pending
 * operation per entity; it is written in the same transaction as the entity change.
 */
@Entity(
    tableName = "sync_operation",
    indices = [Index(value = ["entityType", "entityId"], unique = true)],
)
data class SyncOperationEntity(
    @PrimaryKey val id: UUID,
    val entityType: SyncEntityType,
    val entityId: UUID,
    val operation: SyncOperation,
    val createdAt: Instant,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val lastErrorCode: String?,
)

enum class SyncEntityType {
    PERSON,
    OCCASION,
    GIFT_IDEA,
    GIFT,
    WISHLIST,
    WISH,
}

enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE,
}
