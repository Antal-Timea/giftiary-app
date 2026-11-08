package app.giftiary.data.photos

import android.util.Log
import app.giftiary.data.local.dao.GiftDao
import app.giftiary.data.local.dao.PersonDao
import app.giftiary.data.local.dao.PhotoTransferDao
import app.giftiary.data.local.dao.SyncOperationDao
import app.giftiary.data.local.entity.PhotoTransferKind
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.remote.GiftPhotoRemote
import app.giftiary.data.remote.RemoteAccount
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gift and person photos' part of a sync drain, around the push of the rows. Both live in the
 * same bucket and folder, so a row of either kind is a reference to the photo it names.
 *
 * - [upload] first: a photo selected on this device goes to Storage before any row naming it is
 *   pushed ([isUploadPending] holds that row back), so the backend never names a missing object.
 * - [deleteUnused] after: a replaced or removed photo's object is deleted only once the row no
 *   longer naming it has been pushed, so other devices never point at a deleted object either.
 * - [pruneLocal] last: photo files no row and no transfer needs any more leave this device.
 *
 * Every step can simply run again: an upload replaces the same path, deleting a gone object
 * succeeds, and nothing is forgotten until it succeeded.
 */
@Singleton
class GiftPhotoSync @Inject constructor(
    private val transfers: PhotoTransferDao,
    private val gifts: GiftDao,
    private val people: PersonDao,
    private val operations: SyncOperationDao,
    private val files: GiftPhotoFiles,
    private val remote: GiftPhotoRemote,
    private val clock: Clock,
) {

    /** Whether the row's current photo is still on this device only, so the row must wait. */
    suspend fun isUploadPending(owner: UUID, entityType: SyncEntityType, entityId: UUID): Boolean {
        val path = when (entityType) {
            SyncEntityType.GIFT -> gifts.findById(entityId, owner)?.photoPath
            SyncEntityType.PERSON -> people.findById(entityId, owner)?.photoPath
            else -> null
        } ?: return false
        return transfers.find(path, owner)?.kind == PhotoTransferKind.UPLOAD
    }

    suspend fun hasPending(owner: UUID, kind: PhotoTransferKind): Boolean = transfers.getAll(owner, kind).isNotEmpty()

    suspend fun upload(account: RemoteAccount): PhotoTransferResult {
        val owner = account.ownerUserId
        var transient = 0
        for (transfer in transfers.getAll(owner, PhotoTransferKind.UPLOAD)) {
            val bytes = files.read(transfer.path)
            if (bytes == null) {
                // Cannot happen through this app (the file is written before the transfer); kept
                // so a lost file cannot hold the row back for ever. The row keeps the path and
                // shows the photo as unavailable.
                Log.w(TAG, "Photo to upload is missing on this device")
                transfers.delete(transfer.path, owner)
                continue
            }
            try {
                remote.upload(account, transfer.path, bytes)
                transfers.delete(transfer.path, owner)
            } catch (e: RemoteException) {
                transfers.recordFailure(transfer.path, owner, e.failure.name)
                when (e.failure) {
                    RemoteFailure.UNAUTHENTICATED, RemoteFailure.ACCOUNT_MISMATCH -> return PhotoTransferResult(transient, sessionRefused = true)
                    RemoteFailure.NETWORK, RemoteFailure.SERVER -> transient++
                    else -> Unit
                }
            }
        }
        return PhotoTransferResult(transient, sessionRefused = false)
    }

    suspend fun deleteUnused(account: RemoteAccount): PhotoTransferResult {
        val owner = account.ownerUserId
        val named = namedPaths(owner)
        val ready = transfers.getAll(owner, PhotoTransferKind.DELETE).filter { transfer ->
            // Still named by a row here, or that row's change not pushed yet: the backend's copy of
            // the row may still name the object.
            transfer.path !in named && operations.findForEntity(transfer.entityType, transfer.entityId) == null
        }
        if (ready.isEmpty()) return PhotoTransferResult(0, sessionRefused = false)
        return try {
            remote.delete(account, ready.map { it.path })
            ready.forEach { transfers.delete(it.path, owner) }
            PhotoTransferResult(0, sessionRefused = false)
        } catch (e: RemoteException) {
            ready.forEach { transfers.recordFailure(it.path, owner, e.failure.name) }
            when (e.failure) {
                RemoteFailure.UNAUTHENTICATED, RemoteFailure.ACCOUNT_MISMATCH -> PhotoTransferResult(0, sessionRefused = true)
                RemoteFailure.NETWORK, RemoteFailure.SERVER -> PhotoTransferResult(ready.size, sessionRefused = false)
                else -> PhotoTransferResult(0, sessionRefused = false)
            }
        }
    }

    /**
     * Deletes the owner's photo files that no row names and no transfer needs: replaced photos,
     * downloads of photos replaced elsewhere, and photos picked in an editor that was left without
     * saving. Files younger than [UNSAVED_GRACE] are kept, so an editor still open keeps its pick.
     */
    suspend fun pruneLocal(owner: UUID) {
        val needed = namedPaths(owner) + transfers.getPaths(owner)
        val cutoff = clock.millis() - UNSAVED_GRACE.toMillis()
        files.list(owner)
            .filter { (path, modified) -> path !in needed && modified < cutoff }
            .forEach { (path, _) -> files.delete(path) }
    }

    private suspend fun namedPaths(owner: UUID): Set<String> = (gifts.getPhotoPaths(owner) + people.getPhotoPaths(owner)).toSet()

    private companion object {
        const val TAG = "GiftPhotoSync"
        val UNSAVED_GRACE: Duration = Duration.ofDays(1)
    }
}

/** [transientFailures]: worth trying again later; [sessionRefused]: the token was not accepted. */
data class PhotoTransferResult(val transientFailures: Int, val sessionRefused: Boolean)
