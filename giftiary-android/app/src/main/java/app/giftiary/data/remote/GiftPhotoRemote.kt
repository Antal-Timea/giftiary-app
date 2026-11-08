package app.giftiary.data.remote

import io.github.jan.supabase.storage.BucketApi
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import javax.inject.Inject

/** Gift photo objects in the private Storage bucket, each call made as one captured account. */
interface GiftPhotoRemote {

    /** Stores [bytes] at [path]; uploading the same path again replaces it, so a retry is safe. @throws RemoteException */
    suspend fun upload(account: RemoteAccount, path: String, bytes: ByteArray)

    /** @throws RemoteException */
    suspend fun download(account: RemoteAccount, path: String): ByteArray

    /** Removing an object that is already gone succeeds. @throws RemoteException */
    suspend fun delete(account: RemoteAccount, paths: List<String>)

    /** Removes every object in the account's folder, page by page. @throws RemoteException */
    suspend fun deleteAll(account: RemoteAccount)
}

class SupabaseGiftPhotoRemote @Inject constructor(
    private val clients: AccountBoundClients,
) : GiftPhotoRemote {

    override suspend fun upload(account: RemoteAccount, path: String, bytes: ByteArray) {
        clients.call(account) { client ->
            client.storage.from(BUCKET).upload(path, bytes) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
        }
    }

    override suspend fun download(account: RemoteAccount, path: String): ByteArray =
        clients.call(account) { client -> client.storage.from(BUCKET).downloadAuthenticated(path) }

    override suspend fun delete(account: RemoteAccount, paths: List<String>) {
        if (paths.isEmpty()) return
        clients.call(account) { client -> client.storage.from(BUCKET).delete(paths) }
    }

    override suspend fun deleteAll(account: RemoteAccount) {
        clients.call(account) { client -> client.storage.from(BUCKET).emptyFolder(account.ownerUserId.toString()) }
    }

    companion object {
        const val BUCKET = "gift-photos"
    }
}

/** Removes every object in [folder], page by page. */
internal suspend fun BucketApi.emptyFolder(folder: String) {
    while (true) {
        // Deleted objects leave the listing, so each page starts again from the top.
        val page = list(folder) { limit = PAGE_SIZE }.filter { it.id != null }
        if (page.isEmpty()) break
        delete(page.map { "$folder/${it.name}" })
        if (page.size < PAGE_SIZE) break
    }
}

private const val PAGE_SIZE = 100
