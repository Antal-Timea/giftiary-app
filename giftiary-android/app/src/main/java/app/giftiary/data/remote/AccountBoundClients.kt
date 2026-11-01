package app.giftiary.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.resumable.MemoryResumableCache
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Database clients that can only ever act as one captured account.
 *
 * The app's main client resolves the access token from the global Auth session on every request,
 * and may refresh it there, so a request could leave as whichever account is signed in at that
 * moment. These clients have no Auth plugin: their only token is the captured one, so every
 * request of [withClient], including later pages of a paged read, is made as that account.
 */
class AccountBoundClients(
    private val supabaseUrl: String,
    private val publishableKey: String,
    // Shared by all these short-lived clients; closing a client leaves an engine it was given open.
    private val engine: HttpClientEngine,
) {

    suspend fun <T> withClient(account: RemoteAccount, block: suspend (SupabaseClient) -> T): T {
        val client = createSupabaseClient(supabaseUrl, publishableKey) {
            // The SDK logs request details at debug level.
            defaultLogLevel = LogLevel.WARNING
            httpEngine = engine
            accessToken = { account.accessToken }
            install(Postgrest)
            install(Storage) {
                // Photos are small single-request uploads. The default cache of resumable upload
                // URLs would be a settings file of its own, created on first use of a bucket.
                resumable { cache = MemoryResumableCache() }
            }
        }
        try {
            return block(client)
        } finally {
            withContext(NonCancellable) { client.close() }
        }
    }
}
