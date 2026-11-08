package app.giftiary.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * A gift or person photo's pending Storage work on this device; local only, never synced. Written
 * in the same transaction as the row change that needs it, so a selected photo is never lost: the
 * upload survives restarts until it succeeds, and a replaced or removed photo's object is
 * eventually deleted.
 */
@Entity(
    tableName = "photo_transfer",
    indices = [Index(value = ["ownerUserId", "createdAt"])],
)
data class PhotoTransferEntity(
    /** The Storage path, "<owner>/<photo id>.jpg"; also the local file's path. One transfer per photo. */
    @PrimaryKey val path: String,
    val ownerUserId: UUID,
    /** The row (a gift or a person) that named the photo when this was queued. */
    val entityType: SyncEntityType,
    val entityId: UUID,
    val kind: PhotoTransferKind,
    val createdAt: Instant,
    val attemptCount: Int,
    val lastErrorCode: String?,
)

enum class PhotoTransferKind {
    /** The photo is on this device only; the row naming it is not pushed until this succeeds. */
    UPLOAD,

    /** No row names the photo any more; its object is removed once the row's change has been pushed. */
    DELETE,
}
