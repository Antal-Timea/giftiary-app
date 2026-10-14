package app.gifty.data.remote

import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemoteRow
import app.gifty.data.remote.dto.RemoteSyncMutation
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
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

    override suspend fun findPerson(account: RemoteAccount, id: UUID): RemotePerson? = findRow(account, "people", id)

    override suspend fun findOccasion(account: RemoteAccount, id: UUID): RemoteOccasion? = findRow(account, "occasions", id)

    override suspend fun findGiftIdea(account: RemoteAccount, id: UUID): RemoteGiftIdea? = findRow(account, "gift_ideas", id)

    override suspend fun findGift(account: RemoteAccount, id: UUID): RemoteGift? = findRow(account, "gifts", id)

    override suspend fun findGiftBySourceIdea(account: RemoteAccount, giftIdeaId: UUID): RemoteGift? {
        val rows = clients.call(account) { client ->
            client.from("gifts").select {
                filter {
                    eq("source_gift_idea_id", giftIdeaId)
                    eq("owner_user_id", account.ownerUserId)
                }
                // Two, so that a second row is seen and refused rather than cut off.
                limit(2)
            }.decodeList<RemoteGift>()
        }
        // Evidence that another gift holds the idea, so an untrustworthy answer is an error, never "none".
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() ?: throw RemoteException(RemoteFailure.SERVER)
        if (row.sourceGiftIdeaId != giftIdeaId) throw RemoteException(RemoteFailure.SERVER)
        requireOwner(account, row.ownerUserId)
        return row
    }

    override suspend fun findMutation(account: RemoteAccount, mutationId: UUID): RemoteSyncMutation? {
        val rows = clients.call(account) { client ->
            client.from("sync_mutations").select {
                filter {
                    eq("mutation_id", mutationId)
                    eq("owner_user_id", account.ownerUserId)
                }
                // Two, not one: a second row must be seen to be refused, not hidden by the limit.
                limit(2)
            }.decodeList<RemoteSyncMutation>()
        }
        // This is proof of a commit, so anything but no row or exactly the requested one is an
        // error; a response that cannot be trusted must never pass as "not found" either.
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() ?: throw RemoteException(RemoteFailure.SERVER)
        if (row.mutationId != mutationId) throw RemoteException(RemoteFailure.SERVER)
        requireOwner(account, row.ownerUserId)
        return row
    }

    private suspend inline fun <reified T : RemoteRow> findRow(account: RemoteAccount, table: String, id: UUID): T? {
        val rows = clients.call(account) { client ->
            client.from(table).select {
                filter {
                    eq("id", id)
                    eq("owner_user_id", account.ownerUserId)
                }
                // Two, so that a second row is seen and refused rather than cut off.
                limit(2)
            }.decodeList<T>()
        }
        if (rows.isEmpty()) return null
        val row = rows.singleOrNull() ?: throw RemoteException(RemoteFailure.SERVER)
        if (row.id != id) throw RemoteException(RemoteFailure.SERVER)
        requireOwner(account, row.ownerUserId)
        return row
    }

    private suspend inline fun <reified T : RemoteRow> fetchAll(account: RemoteAccount, table: String): List<T> =
        clients.call(account) { client ->
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

    companion object {
        // Below the default PostgREST response cap of Supabase projects (1000 rows).
        const val PAGE_SIZE = 500
    }
}
