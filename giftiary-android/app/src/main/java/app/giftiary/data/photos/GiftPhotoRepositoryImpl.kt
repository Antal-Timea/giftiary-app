package app.giftiary.data.photos

import android.content.Context
import androidx.core.net.toUri
import app.giftiary.data.local.dao.PhotoTransferDao
import app.giftiary.data.local.entity.PhotoTransferKind
import app.giftiary.data.remote.GiftPhotoRemote
import app.giftiary.data.remote.RemoteAccounts
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.user.ActiveOwner
import app.giftiary.data.user.scoped
import app.giftiary.domain.repository.GiftPhotoRepository
import app.giftiary.domain.repository.GiftPhotoUnreadableException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

class GiftPhotoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: GiftPhotoFiles,
    private val processor: GiftPhotoProcessor,
    private val transfers: PhotoTransferDao,
    private val remote: GiftPhotoRemote,
    private val accounts: RemoteAccounts,
    private val activeOwner: ActiveOwner,
) : GiftPhotoRepository {

    override suspend fun importPhoto(source: String): String {
        val owner = activeOwner.requireOwnerId()
        return withContext(Dispatchers.IO) {
            val uri = source.toUri()
            val bytes = try {
                processor.process { context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unreadable data, a revoked grant or a file that is gone: all the same to the user.
                throw GiftPhotoUnreadableException()
            }
            files.newPath(owner).also { files.write(it, bytes) }
        }
    }

    override suspend fun loadPhoto(path: String): ByteArray? {
        val owner = activeOwner.ownerId.value ?: return null
        // Only the active account's own photos, never a path another account's row could name.
        if (GiftPhotoFiles.ownerOf(path) != owner) return null
        return withContext(Dispatchers.IO) {
            files.read(path) ?: try {
                remote.download(accounts.capture(owner), path).also { files.write(path, it) }
            } catch (_: RemoteException) {
                null
            }
        }
    }

    override fun observeUploadPending(path: String): Flow<Boolean> = activeOwner.scoped(false) { owner ->
        if (GiftPhotoFiles.ownerOf(path) != owner) flowOf(false) else transfers.observeExists(path, owner, PhotoTransferKind.UPLOAD)
    }
}
