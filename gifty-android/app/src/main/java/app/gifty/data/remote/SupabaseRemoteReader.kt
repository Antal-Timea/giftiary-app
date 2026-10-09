package app.gifty.data.remote

import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemoteRow
import app.gifty.data.remote.dto.RemoteSyncMutation
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.UUID

/**
 * [RemoteReader] over PostgREST. Each call gets its own client bound to the captured account.
 *
 * Tables are read in pages by keyset: `owner_user_id = A and id > <last id of the previous page>
 * order by id limit pageSize`, so every row is read exactly once even when the table is larger
 * than any single response. Reading stops at the first empty page rather than the first short
 * one, because the server may cap a response below the requested limit.
 *
 * The owner filter only narrows the query; row level security is what restricts it. Every row is
 * still checked to belong to the account, so a response for anyone else is never handed on.
 */
class SupabaseRemoteReader(
    private val clients: AccountBoundClients,
    private val pageSize: Int = PAGE_SIZE,
) : RemoteReader {

    init {
        require(pageSize > 0) { "pageSize must be positive" }
    }

    override suspend fun fetchPeople(account: RemoteAccount): List<RemotePerson> = fetchAll(account, "people")

    override suspend fun fetchOccasions(account: RemoteAccount): List<RemoteOccasion> = fetchAll(account, "occasions")

    override suspend fun fetchGiftIdeas(account: RemoteAccount): List<RemoteGiftIdea> = fetchAll(account, "gift_ideas")

    override suspend fun fetchGifts(account: RemoteAccount): List<RemoteGift> = fetchAll(account, "gifts")

    override suspend fun findMutation(account: RemoteAccount, mutationId: UUID): RemoteSyncMutation? =
        read(account) { client ->
            client.from("sync_mutations").select {
                filter {
                    eq("mutation_id", mutationId)
                    eq("owner_user_id", account.ownerUserId)
                }
                limit(1)
            }.decodeList<RemoteSyncMutation>()
        }.onEach { requireOwner(account, it.ownerUserId) }
            .singleOrNull()

    private suspend inline fun <reified T : RemoteRow> fetchAll(account: RemoteAccount, table: String): List<T> =
        read(account) { client ->
            val rows = mutableListOf<T>()
            val seen = HashSet<UUID>()
            var after: UUID? = null
            while (true) {
                val page = client.from(table).select {
                    filter {
                        eq("owner_user_id", account.ownerUserId)
                        after?.let { gt("id", it) }
                    }
                    order("id", Order.ASCENDING)
                    limit(pageSize.toLong())
                }.decodeList<T>()
                if (page.isEmpty()) break
                page.forEach { row ->
                    requireOwner(account, row.ownerUserId)
                    // A repeated row means the paging went wrong; stop instead of looping.
                    if (!seen.add(row.id)) throw RemoteException(RemoteFailure.SERVER)
                }
                rows += page
                after = page.last().id
            }
            rows
        }

    private fun requireOwner(account: RemoteAccount, ownerUserId: UUID) {
        if (ownerUserId != account.ownerUserId) throw RemoteException(RemoteFailure.SERVER)
    }

    private suspend fun <T> read(account: RemoteAccount, block: suspend (SupabaseClient) -> T): T = try {
        clients.withClient(account, block)
    } catch (e: CancellationException) {
        throw e
    } catch (e: RemoteException) {
        throw e
    } catch (e: RestException) {
        throw RemoteException(failureFor(e.statusCode), e)
    } catch (e: IOException) {
        throw RemoteException(RemoteFailure.NETWORK, e)
    } catch (e: SerializationException) {
        throw RemoteException(RemoteFailure.SERVER, e)
    }

    private fun failureFor(statusCode: Int) = when (statusCode) {
        401 -> RemoteFailure.UNAUTHENTICATED
        403 -> RemoteFailure.FORBIDDEN
        else -> RemoteFailure.SERVER
    }

    companion object {
        // Below the default PostgREST response cap of Supabase projects (1000 rows).
        const val PAGE_SIZE = 500
    }
}
