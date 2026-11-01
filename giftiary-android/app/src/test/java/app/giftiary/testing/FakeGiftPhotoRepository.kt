package app.giftiary.testing

import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftPhotoUnreadableException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Photos in memory: an import stores the source's bytes under a new path. */
class FakeGiftPhotoRepository : GiftPhotoRepository {

    val stored = mutableMapOf<String, ByteArray>()
    val pendingUploads = MutableStateFlow<Set<String>>(emptySet())
    val imported = mutableListOf<String>()

    /** Sources that cannot be read as images. */
    val unreadable = mutableSetOf<String>()
    private var next = 0

    override suspend fun importPhoto(source: String): String {
        imported += source
        if (source in unreadable) throw GiftPhotoUnreadableException()
        val path = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-${(++next).toString().padStart(12, '0')}.jpg"
        stored[path] = source.encodeToByteArray()
        return path
    }

    override suspend fun loadPhoto(path: String): ByteArray? = stored[path]

    override fun observeUploadPending(path: String): Flow<Boolean> = pendingUploads.map { path in it }
}
