package app.giftiary.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID
import javax.inject.Inject

/**
 * The server side of a device proof: a secret this device registers while signed in, whose hash goes
 * with the account when it is deleted. See the migration `device_account_proofs`.
 */
interface DeviceProofRemote {

    /**
     * Registers [proofHash] (lower-case hex SHA-256 of the secret) for [owner], as [owner] itself.
     * Registering the same hash again succeeds.
     *
     * @throws RemoteException
     */
    suspend fun register(owner: UUID, proofHash: String)

    /**
     * Whether [secret] still belongs to a live account. Asked with no account at all, so it works
     * once the account's session is gone. Only a well-formed `true` or `false` answer returns;
     * anything else throws.
     *
     * @throws RemoteException
     */
    suspend fun isLive(secret: String): Boolean
}

class SupabaseDeviceProofRemote @Inject constructor(
    private val clients: AccountBoundClients,
    private val accounts: RemoteAccounts,
) : DeviceProofRemote {

    override suspend fun register(owner: UUID, proofHash: String) {
        clients.call(accounts.capture(owner)) { client ->
            client.postgrest.rpc("register_device_proof", buildJsonObject { put("p_proof_hash", proofHash) })
        }
    }

    override suspend fun isLive(secret: String): Boolean {
        val body = clients.callWithoutAccount { client ->
            client.postgrest.rpc("device_proof_is_live", buildJsonObject { put("p_proof", secret) }).data
        }
        // "false" lets the device delete an account's data, so nothing but the exact literal is read
        // as it: an empty, quoted, null or otherwise unexpected body is a server failure.
        return when (body.trim()) {
            "true" -> true
            "false" -> false
            else -> throw RemoteException(RemoteFailure.SERVER)
        }
    }
}
