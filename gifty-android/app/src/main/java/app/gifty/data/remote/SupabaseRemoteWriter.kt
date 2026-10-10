package app.gifty.data.remote

import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftFields
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteGiftIdeaFields
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemoteOccasionFields
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemotePersonFields
import app.gifty.data.remote.dto.RemoteRow
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.UUID

/**
 * [RemoteWriter] over plain PostgREST inserts and updates, made through the captured account's
 * client.
 *
 * Every write asks for the written row back (`Prefer: return=representation`), and only that row
 * counts as proof: a write is [Committed] when the server returns exactly the row with the
 * expected id, owner and this write's mutation id, at the version the server's trigger must have
 * given it. A success status alone proves nothing.
 *
 * An update filters on id, owner and version; the server's trigger then sets the new version and
 * records the mutation in the ledger, in the same transaction as the row change.
 */
class SupabaseRemoteWriter(
    private val clients: AccountBoundClients,
) : RemoteWriter {

    override suspend fun insertPerson(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemotePersonFields) =
        insert<RemotePerson>(account, "people", id, mutationId, json.encodeToJsonElement(RemotePersonFields.serializer(), fields))

    override suspend fun insertOccasion(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteOccasionFields) =
        insert<RemoteOccasion>(
            account, "occasions", id, mutationId, json.encodeToJsonElement(RemoteOccasionFields.serializer(), fields),
        )

    override suspend fun insertGiftIdea(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteGiftIdeaFields) =
        insert<RemoteGiftIdea>(
            account, "gift_ideas", id, mutationId, json.encodeToJsonElement(RemoteGiftIdeaFields.serializer(), fields),
        )

    override suspend fun insertGift(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteGiftFields) =
        insert<RemoteGift>(account, "gifts", id, mutationId, json.encodeToJsonElement(RemoteGiftFields.serializer(), fields))

    override suspend fun updatePerson(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemotePersonFields,
    ) = update<RemotePerson>(
        account, "people", id, expectedVersion, mutationId, json.encodeToJsonElement(RemotePersonFields.serializer(), fields),
    )

    override suspend fun updateOccasion(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteOccasionFields,
    ) = update<RemoteOccasion>(
        account, "occasions", id, expectedVersion, mutationId,
        json.encodeToJsonElement(RemoteOccasionFields.serializer(), fields),
    )

    override suspend fun updateGiftIdea(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteGiftIdeaFields,
    ) = update<RemoteGiftIdea>(
        account, "gift_ideas", id, expectedVersion, mutationId,
        json.encodeToJsonElement(RemoteGiftIdeaFields.serializer(), fields),
    )

    override suspend fun updateGift(
        account: RemoteAccount,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: RemoteGiftFields,
    ) = update<RemoteGift>(
        account, "gifts", id, expectedVersion, mutationId, json.encodeToJsonElement(RemoteGiftFields.serializer(), fields),
    )

    private suspend inline fun <reified T : RemoteRow> insert(
        account: RemoteAccount,
        table: String,
        id: UUID,
        mutationId: UUID,
        fields: JsonElement,
    ): Committed {
        // The owner always comes from the captured account, never from the caller.
        val row = JsonObject(
            fields.jsonObject + mapOf(
                "id" to JsonPrimitive(id.toString()),
                "owner_user_id" to JsonPrimitive(account.ownerUserId.toString()),
                "last_mutation_id" to JsonPrimitive(mutationId.toString()),
            ),
        )
        val written = clients.call(account) { client ->
            client.from(table).insert(JsonArray(listOf(row))) { select() }.decodeList<T>()
        }
        val inserted = written.singleOrNull() ?: throw RemoteException(RemoteFailure.SERVER)
        // The server starts every row at version 1.
        return confirm(inserted, account, id, mutationId, resultingVersion = 1)
    }

    private suspend inline fun <reified T : RemoteRow> update(
        account: RemoteAccount,
        table: String,
        id: UUID,
        expectedVersion: Long,
        mutationId: UUID,
        fields: JsonElement,
    ): UpdateOutcome {
        // Backend versions start at 1, and the next version must exist. Anything else is a bug in
        // the caller, never something to send and report back as a missing match.
        require(expectedVersion in 1 until Long.MAX_VALUE) { "Impossible expected version $expectedVersion" }
        val changes = JsonObject(fields.jsonObject + ("last_mutation_id" to JsonPrimitive(mutationId.toString())))
        val written = clients.call(account) { client ->
            client.from(table).update(changes) {
                filter {
                    eq("id", id)
                    eq("owner_user_id", account.ownerUserId)
                    eq("version", expectedVersion)
                }
                select()
            }.decodeList<T>()
        }
        return when (written.size) {
            0 -> NoMatch
            // Every accepted update moves the row exactly one version forward.
            1 -> confirm(written.single(), account, id, mutationId, resultingVersion = expectedVersion + 1)
            else -> throw RemoteException(RemoteFailure.SERVER)
        }
    }

    private fun confirm(row: RemoteRow, account: RemoteAccount, id: UUID, mutationId: UUID, resultingVersion: Long): Committed {
        val isThisWrite = row.id == id && row.ownerUserId == account.ownerUserId && row.lastMutationId == mutationId
        if (!isThisWrite || row.version != resultingVersion) throw RemoteException(RemoteFailure.SERVER)
        return Committed(row.version)
    }

    private companion object {
        // Nulls are encoded: a write sets every column it names, including clearing it.
        val json = Json { explicitNulls = true }
    }
}
