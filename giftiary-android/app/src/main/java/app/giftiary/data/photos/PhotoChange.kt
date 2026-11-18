package app.giftiary.data.photos

import app.giftiary.data.local.dao.PhotoTransferDao
import app.giftiary.data.local.entity.PhotoTransferEntity
import app.giftiary.data.local.entity.PhotoTransferKind
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import java.time.Instant
import java.util.UUID

/**
 * Queues the Storage work a gift's, gift idea's, person's or wish's photo change needs. Called inside the row's write
 * transaction, after the row itself was written: the new photo's upload, and the old one's removal
 * (or, if it was never uploaded, nothing to remove).
 *
 * Returns the old photo's path, whose file the caller deletes once the transaction committed; null
 * when there is nothing to delete, including when another row still names the old photo.
 *
 * @throws GiftPhotoUnavailableException if [new] was not imported for this account or is no longer
 *   on this device.
 */
internal suspend fun queuePhotoChange(
    transfers: PhotoTransferDao,
    files: GiftPhotoFiles,
    owner: UUID,
    entityType: SyncEntityType,
    entityId: UUID,
    old: String?,
    new: String?,
    now: Instant,
): String? {
    if (old == new) return null
    if (new != null) {
        if (GiftPhotoFiles.ownerOf(new) != owner || !files.exists(new)) throw GiftPhotoUnavailableException()
        transfers.put(PhotoTransferEntity(new, owner, entityType, entityId, PhotoTransferKind.UPLOAD, now, 0, null))
    }
    // The app never gives two rows one photo; if they shared one, neither its file nor its pending
    // upload may go while the other still shows it.
    if (old == null || transfers.isNamed(old, owner)) return null
    if (transfers.find(old, owner)?.kind == PhotoTransferKind.UPLOAD) {
        transfers.delete(old, owner)
    } else {
        transfers.put(PhotoTransferEntity(old, owner, entityType, entityId, PhotoTransferKind.DELETE, now, 0, null))
    }
    return old
}
