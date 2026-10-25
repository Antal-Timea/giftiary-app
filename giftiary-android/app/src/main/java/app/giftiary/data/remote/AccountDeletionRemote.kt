package app.giftiary.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject

/** The server side of deleting an account. */
interface AccountDeletionRemote {

    /**
     * Deletes [owner]'s account and all its data on the server, as [owner] itself. Deleting an
     * account that is already gone succeeds, so a retry after a lost answer is safe.
     *
     * @throws RemoteException
     */
    suspend fun deleteAccount(owner: UUID)
}

class SupabaseAccountDeletionRemote @Inject constructor(
    private val clients: AccountBoundClients,
    private val accounts: RemoteAccounts,
) : AccountDeletionRemote {

    // No argument: the server deletes whoever the token belongs to, and nobody else.
    override suspend fun deleteAccount(owner: UUID) {
        clients.call(accounts.capture(owner)) { client -> client.postgrest.rpc("delete_my_account", JsonObject(emptyMap())) }
    }
}
