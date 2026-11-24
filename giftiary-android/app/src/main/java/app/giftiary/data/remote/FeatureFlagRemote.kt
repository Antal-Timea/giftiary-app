package app.giftiary.data.remote

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * Switches the project owner flips in the Supabase dashboard (table `feature_flags`, see the
 * migration `feature_flags`), read before anyone signs in.
 */
interface FeatureFlagRemote {

    /**
     * Whether the flag [key] is on. A flag with no row is off.
     *
     * @throws RemoteException
     */
    suspend fun isEnabled(key: String): Boolean
}

class SupabaseFeatureFlagRemote @Inject constructor(
    private val clients: AccountBoundClients,
) : FeatureFlagRemote {

    // Asked with no account: the sign-in screen needs the answer before there is one, and a flag
    // must read the same for everybody.
    override suspend fun isEnabled(key: String): Boolean {
        val rows = clients.callWithoutAccount { client ->
            client.postgrest.from("feature_flags")
                .select { filter { eq("key", key) } }
                .decodeList<FeatureFlagRow>()
        }
        return rows.singleOrNull()?.enabled ?: false
    }

    @Serializable
    private class FeatureFlagRow(val enabled: Boolean)
}
