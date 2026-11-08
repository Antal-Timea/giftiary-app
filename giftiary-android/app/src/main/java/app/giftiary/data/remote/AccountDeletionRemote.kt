package app.giftiary.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject

/** The server side of deleting an account. */
interface AccountDeletionRemote {

    /**
     * Deletes [owner]'s account and all its data on the server, its photos and avatar included, as
     * [owner] itself. Deleting an account that is already gone succeeds, so a retry after a lost
     * answer is safe.
     *
     * @throws RemoteException
     */
    suspend fun deleteAccount(owner: UUID)
}

class SupabaseAccountDeletionRemote @Inject constructor(
    private val clients: AccountBoundClients,
    private val accounts: RemoteAccounts,
    private val photos: GiftPhotoRemote,
    private val avatars: ProfileAvatarRemote,
) : AccountDeletionRemote {

    // The account's photos and avatars first: Storage objects can only be removed through the Storage API,
    // never by the database function, and after the account is gone nobody could reach them. If
    // this fails the account and its rows stay, though some photos may already be gone, and the
    // whole deletion is tried again.
    // No argument: the server deletes whoever the token belongs to, and nobody else.
    override suspend fun deleteAccount(owner: UUID) {
        val account = accounts.capture(owner)
        photos.deleteAll(account)
        avatars.deleteAll(account)
        clients.call(account) { client -> client.postgrest.rpc("delete_my_account", JsonObject(emptyMap())) }
    }
}
