package app.giftiary.data.remote

import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import javax.inject.Inject

/**
 * Profile avatars in their own private Storage bucket, apart from gift and person photos, so that
 * nothing able to read those (now or in a later sharing feature) can reach an avatar.
 */
interface ProfileAvatarRemote {

    /** Uploading the same path again replaces it, so a retry is safe. @throws RemoteException */
    suspend fun upload(account: RemoteAccount, path: String, bytes: ByteArray)

    /** @throws RemoteException */
    suspend fun download(account: RemoteAccount, path: String): ByteArray

    /** @throws RemoteException */
    suspend fun delete(account: RemoteAccount, paths: List<String>)

    /** The account's avatar objects, at most [limit]. @throws RemoteException */
    suspend fun list(account: RemoteAccount, limit: Int): List<StoredAvatar>

    /** @throws RemoteException */
    suspend fun deleteAll(account: RemoteAccount)
}

/** [createdAtMillis] is null when Storage does not say. */
data class StoredAvatar(val path: String, val createdAtMillis: Long?)

class SupabaseProfileAvatarRemote @Inject constructor(
    private val clients: AccountBoundClients,
) : ProfileAvatarRemote {

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

    override suspend fun list(account: RemoteAccount, limit: Int): List<StoredAvatar> {
        val folder = account.ownerUserId.toString()
        return clients.call(account) { client ->
            client.storage.from(BUCKET).list(folder) { this.limit = limit }
                .filter { it.id != null }
                .map { StoredAvatar("$folder/${it.name}", it.createdAt?.toEpochMilliseconds()) }
        }
    }

    override suspend fun deleteAll(account: RemoteAccount) {
        clients.call(account) { client -> client.storage.from(BUCKET).emptyFolder(account.ownerUserId.toString()) }
    }

    companion object {
        const val BUCKET = "profile-avatars"
    }
}
