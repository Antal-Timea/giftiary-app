package app.giftiary.testing

import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftPhotoUnreadableException
import app.giftiary.domain.repository.PhotoKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Photos in memory: an import stores the source's bytes under a new path. */
class FakeGiftPhotoRepository : GiftPhotoRepository {

    val stored = mutableMapOf<String, ByteArray>()
    val pendingUploads = MutableStateFlow<Set<String>>(emptySet())
    val imported = mutableListOf<String>()

    /** The kind each import asked for, in order. */
    val importedKinds = mutableListOf<PhotoKind>()

    /** Paths [loadPhoto] can reach only by downloading: [loadLocalPhoto] does not return them. */
    val remoteOnly = mutableSetOf<String>()

    /** Sources that cannot be read as images. */
    val unreadable = mutableSetOf<String>()
    private var next = 0

    override suspend fun importPhoto(source: String, kind: PhotoKind): String {
        imported += source
        importedKinds += kind
        if (source in unreadable) throw GiftPhotoUnreadableException()
        val path = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-${(++next).toString().padStart(12, '0')}.jpg"
        stored[path] = source.encodeToByteArray()
        return path
    }

    /** Capture targets handed out and not yet imported or discarded. */
    val openCaptures = mutableSetOf<String>()
    val discarded = mutableListOf<String>()
    private var nextCapture = 0

    override fun newCaptureTarget(): String = "content://app.giftiary.photos/camera/${++nextCapture}.jpg".also { openCaptures += it }

    /** What the camera wrote into a target; a target without an entry is read as unreadable. */
    val captured = mutableMapOf<String, String>()

    override suspend fun importCapture(target: String, kind: PhotoKind): String {
        openCaptures -= target
        val content = captured[target] ?: throw GiftPhotoUnreadableException()
        return importPhoto(content, kind)
    }

    override suspend fun discardCapture(target: String) {
        openCaptures -= target
        discarded += target
    }

    /** Every path [loadPhoto] and [loadLocalPhoto] were asked for, in order. */
    val loads = mutableListOf<String>()
    val localLoads = mutableListOf<String>()

    /** Thrown by the next [loadPhoto] calls, one each, standing in for a failing file read. */
    val loadFailures = ArrayDeque<Exception>()

    override suspend fun loadPhoto(path: String): ByteArray? {
        loads += path
        loadFailures.removeFirstOrNull()?.let { throw it }
        return stored[path]
    }

    /** Every path [loadSharedPhoto] was asked for, in order. */
    val sharedLoads = mutableListOf<String>()

    override suspend fun loadSharedPhoto(path: String): ByteArray? {
        sharedLoads += path
        return stored[path]
    }

    override suspend fun loadLocalPhoto(path: String): ByteArray? {
        localLoads += path
        return stored[path]?.takeUnless { path in remoteOnly }
    }

    override fun observeUploadPending(path: String): Flow<Boolean> = pendingUploads.map { path in it }
}
