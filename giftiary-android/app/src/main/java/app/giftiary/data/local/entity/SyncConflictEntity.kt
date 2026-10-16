package app.giftiary.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Local changes the backend can never take. The backend wins locally; the losing local state is
 * kept here so the user does not silently lose it. Two kinds, told apart by [baseVersion]:
 * - a change of a row that had moved on since [baseVersion] (1 or more): the backend's row replaces
 *   the local one;
 * - a gift create (base version 0) refused because another gift of the account already holds its
 *   source idea: the local gift is deleted, and the other one, found through the snapshot's source
 *   idea, replaces it.
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
    /** The version the losing changes were based on; 0 for a refused create. */
    val baseVersion: Long,
    /**
     * The winning row's backend version seen when the conflict was settled, that of the other gift
     * for a refused create; it may have moved on since.
     */
    val remoteVersion: Long,
    /**
     * The entity's latest local state when the conflict was settled, as its Remote*Fields JSON
     * (type given by [entityType]). It may include changes made after [mutationId] was sent.
     */
    val localSnapshotJson: String,
    val createdAt: Instant,
    val seenAt: Instant?,
)
