package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Local changes the backend could not take because the row had moved on since [baseVersion]. The
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
    /**
     * The mutation whose evidence proved the conflict. Not necessarily the source of
     * [localSnapshotJson]: changes made after it was sent are folded into the same snapshot.
     */
    val mutationId: UUID,
    /** The version the losing changes were based on. */
    val baseVersion: Long,
    /** The backend's version seen when the conflict was settled; it may have moved on since. */
    val remoteVersion: Long,
    /**
     * The entity's latest local state when the conflict was settled, as its Remote*Fields JSON
     * (type given by [entityType]). It may include changes made after [mutationId] was sent.
     */
    val localSnapshotJson: String,
    val createdAt: Instant,
    val seenAt: Instant?,
)
