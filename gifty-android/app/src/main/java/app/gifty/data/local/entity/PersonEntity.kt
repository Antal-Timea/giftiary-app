package app.gifty.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Sync metadata shared by all user-owned entities:
 * - [version] is the backend row version this copy is based on, 0 while [RemoteState.LOCAL_ONLY].
 * - [deletedAt] marks a soft-deleted row; normal reads must exclude it.
 */
@Entity(
    tableName = "person",
    // (id, ownerUserId) is the target of the wishlists' same-owner foreign key.
    indices = [Index("ownerUserId"), Index(value = ["id", "ownerUserId"], unique = true)],
)
data class PersonEntity(
    @PrimaryKey val id: UUID,
    val ownerUserId: UUID,
    val name: String,
    val relationship: String?,
    val photoPath: String?,
    val linkedUserId: UUID?,
    val version: Long,
    val remoteState: RemoteState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)
