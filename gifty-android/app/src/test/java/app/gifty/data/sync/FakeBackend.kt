package app.gifty.data.sync

import app.gifty.data.local.GiftyDatabase
import app.gifty.data.remote.Committed
import app.gifty.data.remote.NoMatch
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteException
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteReader
import app.gifty.data.remote.RemoteWriter
import app.gifty.data.remote.UpdateOutcome
import app.gifty.data.remote.dto.RemoteGift
import app.gifty.data.remote.dto.RemoteGiftFields
import app.gifty.data.remote.dto.RemoteGiftIdea
import app.gifty.data.remote.dto.RemoteGiftIdeaFields
import app.gifty.data.remote.dto.RemoteOccasion
import app.gifty.data.remote.dto.RemoteOccasionFields
import app.gifty.data.remote.dto.RemotePerson
import app.gifty.data.remote.dto.RemotePersonFields
import app.gifty.data.remote.dto.RemoteSyncMutation
import kotlinx.coroutines.CompletableDeferred
import java.time.Instant
import java.util.Collections
import java.util.UUID

/**
 * The backend as the writer and reader see it, with its real rules: inserts start at version 1,
 * updates are compare-and-set on owner and version and add one, every mutation id is single-use
 * across all rows (23505 otherwise), referenced rows must exist (23503 otherwise), and a write and
 * its ledger entry appear together.
 *
 * Each write follows the next scripted [Behavior], so tests can lose a request or a response,
 * commit late, refuse, or hold a request open.
 */
class FakeBackend(private val database: GiftyDatabase) : RemoteReader, RemoteWriter {

    class Row(val owner: UUID, var version: Long, var lastMutationId: UUID?, var fields: Any)

    data class Write(
        val insert: Boolean,
        val entityId: UUID,
        val owner: UUID,
        val token: String,
        val expectedVersion: Long?,
        val mutationId: UUID,
        val fields: Any,
    )

    sealed interface Behavior {
        /** Applies the write and answers. */
        data object Normal : Behavior

        /** Applies the write, but the answer never arrives. */
        data object LoseResponse : Behavior

        /** Never reaches the database. */
        data object LoseRequest : Behavior

        /** Gives no answer, then commits just before the next write is handled. */
        data object CommitLater : Behavior

        /**
         * Gives no answer while the request stays alive on the server: it commits (or fails) only
         * when the test calls [commitLingering], at any later point.
         */
        data object Linger : Behavior

        /** Refused without applying anything. */
        data class Fail(val failure: RemoteFailure) : Behavior

        /** Signals [arrived], waits for [release], then behaves like [then]. */
        data class Hold(
            val arrived: CompletableDeferred<Unit> = CompletableDeferred(),
            val release: CompletableDeferred<Unit> = CompletableDeferred(),
            val then: Behavior = Normal,
        ) : Behavior
    }

    val rows: MutableMap<UUID, Row> = Collections.synchronizedMap(mutableMapOf())
    val ledger: MutableMap<UUID, RemoteSyncMutation> = Collections.synchronizedMap(mutableMapOf())
    val writes: MutableList<Write> = Collections.synchronizedList(mutableListOf())
    val script = ArrayDeque<Behavior>()
    var reads = 0
        private set

    /** Set when a request is made inside a Room transaction, which must never happen. */
    var requestInsideTransaction = false
        private set

    /** Makes every read fail, e.g. to lose evidence lookups too. */
    var readFailure: RemoteFailure? = null

    /** Runs right after a row lookup, e.g. to let a lingering write commit at exactly that point. */
    var afterRowRead: (() -> Unit)? = null

    private val lateCommits = mutableListOf<() -> Unit>()
    private val lingering = mutableListOf<() -> Unit>()

    /** Lets every lingering request reach the database now, in the order they were sent. */
    fun commitLingering() {
        val requests = synchronized(lingering) { lingering.toList().also { lingering.clear() } }
        requests.forEach { it() }
    }

    fun seed(id: UUID, owner: UUID, version: Long, lastMutationId: UUID?, fields: Any) {
        rows[id] = Row(owner, version, lastMutationId, fields)
    }

    /** A write committed outside the tests' view, e.g. one whose answer was lost: row and ledger together. */
    fun recordCommit(id: UUID, owner: UUID, version: Long, mutationId: UUID, fields: Any) {
        rows[id] = Row(owner, version, mutationId, fields)
        record(mutationId, owner, tableOf(fields), id, version)
    }

    // ---- writer ----

    override suspend fun insertPerson(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemotePersonFields) =
        write(account, id, null, mutationId, fields) as Committed

    override suspend fun insertOccasion(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteOccasionFields) =
        write(account, id, null, mutationId, fields) as Committed

    override suspend fun insertGiftIdea(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteGiftIdeaFields) =
        write(account, id, null, mutationId, fields) as Committed

    override suspend fun insertGift(account: RemoteAccount, id: UUID, mutationId: UUID, fields: RemoteGiftFields) =
        write(account, id, null, mutationId, fields) as Committed

    override suspend fun updatePerson(
        account: RemoteAccount, id: UUID, expectedVersion: Long, mutationId: UUID, fields: RemotePersonFields,
    ) = write(account, id, expectedVersion, mutationId, fields)

    override suspend fun updateOccasion(
        account: RemoteAccount, id: UUID, expectedVersion: Long, mutationId: UUID, fields: RemoteOccasionFields,
    ) = write(account, id, expectedVersion, mutationId, fields)

    override suspend fun updateGiftIdea(
        account: RemoteAccount, id: UUID, expectedVersion: Long, mutationId: UUID, fields: RemoteGiftIdeaFields,
    ) = write(account, id, expectedVersion, mutationId, fields)

    override suspend fun updateGift(
        account: RemoteAccount, id: UUID, expectedVersion: Long, mutationId: UUID, fields: RemoteGiftFields,
    ) = write(account, id, expectedVersion, mutationId, fields)

    private suspend fun write(account: RemoteAccount, id: UUID, expected: Long?, mutationId: UUID, fields: Any): UpdateOutcome {
        if (database.inTransaction()) requestInsideTransaction = true
        synchronized(lateCommits) {
            lateCommits.forEach { it() }
            lateCommits.clear()
        }
        writes += Write(expected == null, id, account.ownerUserId, account.accessToken, expected, mutationId, fields)
        var behavior = synchronized(script) { script.removeFirstOrNull() } ?: Behavior.Normal
        if (behavior is Behavior.Hold) {
            behavior.arrived.complete(Unit)
            behavior.release.await()
            behavior = behavior.then
        }
        return when (behavior) {
            Behavior.Normal -> apply(account.ownerUserId, id, expected, mutationId, fields)
            Behavior.LoseResponse -> {
                apply(account.ownerUserId, id, expected, mutationId, fields)
                throw RemoteException(RemoteFailure.NETWORK)
            }
            Behavior.LoseRequest -> throw RemoteException(RemoteFailure.NETWORK)
            Behavior.Linger -> {
                synchronized(lingering) {
                    lingering += { runCatching { apply(account.ownerUserId, id, expected, mutationId, fields) } }
                }
                throw RemoteException(RemoteFailure.NETWORK)
            }
            Behavior.CommitLater -> {
                synchronized(lateCommits) {
                    lateCommits += { runCatching { apply(account.ownerUserId, id, expected, mutationId, fields) } }
                }
                throw RemoteException(RemoteFailure.NETWORK)
            }
            is Behavior.Fail -> throw RemoteException(behavior.failure)
            is Behavior.Hold -> error("Nested hold")
        }
    }

    private fun apply(owner: UUID, id: UUID, expected: Long?, mutationId: UUID, fields: Any): UpdateOutcome = synchronized(rows) {
        if (expected == null) {
            if (id in rows || mutationId in ledger) throw RemoteException(RemoteFailure.DUPLICATE)
            requireReferences(owner, fields)
            rows[id] = Row(owner, 1, mutationId, fields)
            record(mutationId, owner, tableOf(fields), id, 1)
            return Committed(1)
        }
        val row = rows[id]?.takeIf { it.owner == owner && it.version == expected } ?: return NoMatch
        if (mutationId in ledger || row.lastMutationId == mutationId) throw RemoteException(RemoteFailure.DUPLICATE)
        requireReferences(owner, fields)
        row.version += 1
        row.lastMutationId = mutationId
        row.fields = fields
        record(mutationId, owner, tableOf(fields), id, row.version)
        Committed(row.version)
    }

    /** The same-owner foreign keys of the real schema. */
    private fun requireReferences(owner: UUID, fields: Any) {
        val references = when (fields) {
            is RemoteOccasionFields -> listOf(fields.personId)
            is RemoteGiftIdeaFields -> listOf(fields.recipientPersonId, fields.occasionId)
            is RemoteGiftFields -> listOf(fields.giverPersonId, fields.recipientPersonId, fields.occasionId, fields.sourceGiftIdeaId)
            else -> emptyList()
        }
        if (references.filterNotNull().any { rows[it]?.owner != owner }) throw RemoteException(RemoteFailure.MISSING_REFERENCE)
    }

    private fun tableOf(fields: Any) = when (fields) {
        is RemotePersonFields -> "people"
        is RemoteOccasionFields -> "occasions"
        is RemoteGiftIdeaFields -> "gift_ideas"
        is RemoteGiftFields -> "gifts"
        else -> error("Unknown fields $fields")
    }

    private fun record(mutationId: UUID, owner: UUID, table: String, id: UUID, version: Long) {
        ledger[mutationId] = RemoteSyncMutation(mutationId, owner, table, id, version, NOW)
    }

    // ---- reader ----

    override suspend fun findMutation(account: RemoteAccount, mutationId: UUID): RemoteSyncMutation? {
        read()
        return ledger[mutationId]?.takeIf { it.ownerUserId == account.ownerUserId }
    }

    override suspend fun findPerson(account: RemoteAccount, id: UUID): RemotePerson? = visible(account, id)?.let { row ->
        val f = row.fields as RemotePersonFields
        RemotePerson(id, row.owner, f.name, f.relationship, f.photoPath, f.linkedUserId, row.version, NOW, NOW, f.deletedAt, row.lastMutationId)
    }

    override suspend fun findOccasion(account: RemoteAccount, id: UUID): RemoteOccasion? = visible(account, id)?.let { row ->
        val f = row.fields as RemoteOccasionFields
        RemoteOccasion(
            id, row.owner, f.personId, f.type, f.title, f.day, f.month, f.year, f.repeatsYearly, row.version, NOW, NOW,
            f.deletedAt, row.lastMutationId,
        )
    }

    override suspend fun findGiftIdea(account: RemoteAccount, id: UUID): RemoteGiftIdea? = visible(account, id)?.let { row ->
        val f = row.fields as RemoteGiftIdeaFields
        RemoteGiftIdea(
            id, row.owner, f.recipientPersonId, f.occasionId, f.name, f.status, f.estimatedPriceAmountMinor,
            f.estimatedPriceCurrencyCode, f.actualPriceAmountMinor, f.actualPriceCurrencyCode, f.photoPath, f.productUrl,
            f.note, row.version, NOW, NOW, f.archivedAt, f.deletedAt, row.lastMutationId,
        )
    }

    override suspend fun findGift(account: RemoteAccount, id: UUID): RemoteGift? = visible(account, id)?.let { row ->
        val f = row.fields as RemoteGiftFields
        RemoteGift(
            id, row.owner, f.direction, f.giverPersonId, f.recipientPersonId, f.occasionId, f.sourceGiftIdeaId, f.name,
            f.givenAt, f.priceAmountMinor, f.priceCurrencyCode, f.photoPath, f.productUrl, f.note, row.version, NOW, NOW,
            f.deletedAt, row.lastMutationId,
        )
    }

    private fun visible(account: RemoteAccount, id: UUID): Row? {
        read()
        return rows[id]?.takeIf { it.owner == account.ownerUserId }.also { afterRowRead?.invoke() }
    }

    private fun read() {
        if (database.inTransaction()) requestInsideTransaction = true
        reads++
        readFailure?.let { throw RemoteException(it) }
    }

    override suspend fun fetchPeople(account: RemoteAccount) = error("Not used by a single push")
    override suspend fun fetchOccasions(account: RemoteAccount) = error("Not used by a single push")
    override suspend fun fetchGiftIdeas(account: RemoteAccount) = error("Not used by a single push")
    override suspend fun fetchGifts(account: RemoteAccount) = error("Not used by a single push")

    private companion object {
        val NOW: Instant = Instant.parse("2026-10-10T12:00:00Z")
    }
}
