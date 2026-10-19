package app.giftiary.data.local.entity

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
    indices = [
        Index(value = ["entityType", "entityId"], unique = true),
        // Pending work is read per owner, oldest first.
        Index(value = ["ownerUserId", "createdAt"]),
    ],
)
data class SyncOperationEntity(
    @PrimaryKey val id: UUID,
    /**
     * The owner of the entity, always set for operations created since schema v2.
     *
     * Null means exactly one thing: the operation was created under the pre-v2 schema, when
     * operations had no owner column. Nothing assigns it an owner any more. It is not a general
     * "unknown owner" state; owner-scoped reads never return such operations.
     */
    val ownerUserId: UUID?,
    val entityType: SyncEntityType,
    val entityId: UUID,
    val operation: SyncOperation,
    /**
     * Identity of the payload this operation currently stands for: the entity's current Room state.
     * Every new local change gets a new random id, so the backend can tell one change from another
     * and a retry of the same change from a new one.
     */
    val mutationId: UUID,
    /**
     * The mutation already sent to the backend whose outcome is not known yet, or null. Local edits
     * never change it: until sync has found out whether it committed, it is the only way to prove
     * that, even when [mutationId] has moved on.
     */
    val inFlightMutationId: UUID?,
    /** Why automatic sync gave up on the current mutation, [mutationId]; a new local change clears it. */
    val blockedReason: String?,
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
