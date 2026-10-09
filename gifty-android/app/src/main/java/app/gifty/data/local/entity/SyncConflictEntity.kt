package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A local change the backend rejected because the row had moved on since [baseVersion]. The
 * backend's row wins locally; the losing local state is kept here so the user does not silently
 * lose it.
 */
@Entity(
    tableName = "sync_conflict",
    indices = [
        // Conflicts are read per owner, unseen ones first.
        Index(value = ["ownerUserId", "seenAt"]),
    ],
)
data class SyncConflictEntity(
    @PrimaryKey val id: UUID,
    // Conflicts only arise while syncing an account, so unlike queue rows they always have an owner.
    val ownerUserId: UUID,
    val entityType: SyncEntityType,
    val entityId: UUID,
    /** The rejected mutation. */
    val mutationId: UUID,
    /** The version the rejected change was based on. */
    val baseVersion: Long,
    /** The backend's version that won. */
    val remoteVersion: Long,
    val localSnapshotJson: String,
    val createdAt: Instant,
    val seenAt: Instant?,
)
