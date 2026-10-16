package app.gifty.data.remote

import app.gifty.data.remote.RemoteTestData.alice
import app.gifty.data.remote.RemoteTestData.bob
import app.gifty.data.remote.RemoteTestData.token
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.Collections
import java.util.UUID

/**
 * The reader against a fake PostgREST: it filters, orders and pages like the real one, and
 * records every request so the token and filters each one carried can be checked.
 */
@RunWith(RobolectricTestRunner::class)
class SupabaseRemoteReaderTest {

    private val alicesToken = token(alice)
    private val bobsToken = token(bob)
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    // Table rows the fake server holds, and how it answers; tests replace the answer when needed.
    private val tables = mutableMapOf<String, List<JsonObject>>()
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { serve(it) }

    private val engine = MockEngine { request ->
        requests += request
        answer(request)
    }
    private val reader = SupabaseRemoteReader(AccountBoundClients(URL, PUBLISHABLE_KEY, engine), pageSize = 2)

    @After
    fun tearDown() {
        engine.close()
    }

    // ---- paging and filtering ----

    @Test
    fun `a table is read completely, page by page in id order, and only for the account`() = runTest {
        val ids = (1..5).map { UUID.fromString("0000000$it-0000-4000-8000-000000000000") }
        tables["people"] = ids.shuffled().map { RemoteTestData.person(it, alice) } +
            RemoteTestData.person(UUID.fromString("00000003-0000-4000-8000-0000000000bb"), bob)

        val people = reader.fetchPeople(alice())

        assertEquals(ids, people.map { it.id })
        assertTrue(people.all { it.ownerUserId == alice })
        // Pages of two: three pages with rows, then an empty one ends the read.
        assertEquals(listOf(null, "gt.${ids[1]}", "gt.${ids[3]}", "gt.${ids[4]}"), requests.map { it.url.parameters["id"] })
        requests.forEach { request ->
            assertEquals("eq.$alice", request.url.parameters["owner_user_id"])
            assertEquals("id.asc.nullslast", request.url.parameters["order"])
            assertEquals("2", request.url.parameters["limit"])
            assertEquals("GET", request.method.value)
        }
    }

    @Test
    fun `a short page does not end the read, since the server may cap responses below the limit`() = runTest {
        val ids = (1..3).map { UUID.fromString("0000000$it-0000-4000-8000-000000000000") }
        tables["people"] = ids.map { RemoteTestData.person(it, alice) }
        // A server whose cap is one row, below the requested two.
        answer = { request -> serve(request, cap = 1) }

        assertEquals(ids, reader.fetchPeople(alice()).map { it.id })
    }

    @Test
    fun `tombstones and archived rows are part of the read`() = runTest {
        val deleted = UUID.fromString("00000001-0000-4000-8000-000000000000")
        val archived = UUID.fromString("00000002-0000-4000-8000-000000000000")
        tables["occasions"] = listOf(RemoteTestData.occasion(deleted, alice, mapOf("deleted_at" to "2026-10-05T08:00:00+00:00")))
        tables["gift_ideas"] = listOf(RemoteTestData.giftIdea(archived, alice))
        tables["gifts"] = listOf(RemoteTestData.gift(deleted, alice))

        assertEquals(listOf(deleted), reader.fetchOccasions(alice()).filter { it.deletedAt != null }.map { it.id })
        assertEquals(listOf(archived), reader.fetchGiftIdeas(alice()).filter { it.archivedAt != null }.map { it.id })
        assertEquals(listOf(deleted), reader.fetchGifts(alice()).filter { it.deletedAt != null }.map { it.id })
        assertEquals(
            listOf("occasions", "gift_ideas", "gifts"),
            requests.map { it.url.encodedPath.substringAfterLast('/') }.distinct(),
        )
        assertTrue(requests.none { it.url.parameters.contains("deleted_at") || it.url.parameters.contains("archived_at") })
    }

    @Test
    fun `a row of another owner in a response is refused, not returned`() = runTest {
        tables["gifts"] = listOf(RemoteTestData.gift(UUID.randomUUID(), alice))
        answer = { respondRows(listOf(RemoteTestData.gift(UUID.randomUUID(), bob))) }

        assertFailure(RemoteFailure.SERVER) { reader.fetchGifts(alice()) }
    }

    @Test
    fun `wishlists and wishes are read in full from their own tables, for the account only, tombstones included`() = runTest {
        val ids = (1..3).map { UUID.fromString("0000000$it-0000-4000-8000-000000000000") }
        tables["wishlists"] = ids.map { RemoteTestData.wishlist(it, alice) } +
            RemoteTestData.wishlist(UUID.fromString("00000002-0000-4000-8000-0000000000bb"), bob)
        tables["wishes"] = ids.map { RemoteTestData.wish(it, alice) }

        assertEquals(ids, reader.fetchWishlists(alice()).map { it.id })
        val wishes = reader.fetchWishes(alice())

        assertEquals(ids, wishes.map { it.id })
        assertTrue("tombstones are read", wishes.all { it.deletedAt != null })
        assertEquals(listOf("wishlists", "wishes"), requests.map { it.url.encodedPath.substringAfterLast('/') }.distinct())
        requests.forEach { assertEquals("eq.$alice", it.url.parameters["owner_user_id"]) }
    }

    @Test
    fun `a wishlist or wish of another owner in a response is refused`() = runTest {
        answer = { respondRows(listOf(RemoteTestData.wishlist(UUID.randomUUID(), bob))) }
        assertFailure(RemoteFailure.SERVER) { reader.fetchWishlists(alice()) }

        answer = { respondRows(listOf(RemoteTestData.wish(UUID.randomUUID(), bob))) }
        assertFailure(RemoteFailure.SERVER) { reader.fetchWishes(alice()) }
    }

    // ---- the mutation ledger ----

    @Test
    fun `a committed mutation is found in the ledger, for the account only`() = runTest {
        val mutationId = UUID.fromString("c0000000-0000-4000-8000-000000000009")
        answer = { respondRows(listOf(RemoteTestData.mutation(mutationId, alice))) }

        val mutation = reader.findMutation(alice(), mutationId)

        assertEquals(mutationId, mutation?.mutationId)
        assertEquals(7L, mutation?.resultingVersion)
        val request = requests.single()
        assertEquals("sync_mutations", request.url.encodedPath.substringAfterLast('/'))
        assertEquals("eq.$mutationId", request.url.parameters["mutation_id"])
        assertEquals("eq.$alice", request.url.parameters["owner_user_id"])
        // Two, so that a second row would be seen and refused rather than cut off.
        assertEquals("2", request.url.parameters["limit"])
    }

    @Test
    fun `a mutation missing from the ledger is reported as not found`() = runTest {
        answer = { respondRows(emptyList()) }

        assertNull(reader.findMutation(alice(), UUID.randomUUID()))
    }

    @Test
    fun `a ledger row for another mutation is refused rather than taken as proof`() = runTest {
        answer = { respondRows(listOf(RemoteTestData.mutation(UUID.randomUUID(), alice))) }

        assertFailure(RemoteFailure.SERVER) { reader.findMutation(alice(), UUID.randomUUID()) }
    }

    @Test
    fun `more than one ledger row is refused rather than passed off as not found`() = runTest {
        val mutationId = UUID.randomUUID()
        answer = {
            respondRows(listOf(RemoteTestData.mutation(mutationId, alice), RemoteTestData.mutation(mutationId, alice)))
        }

        assertFailure(RemoteFailure.SERVER) { reader.findMutation(alice(), mutationId) }
    }

    @Test
    fun `a ledger row of another owner is refused`() = runTest {
        val mutationId = UUID.randomUUID()
        answer = { respondRows(listOf(RemoteTestData.mutation(mutationId, bob))) }

        assertFailure(RemoteFailure.SERVER) { reader.findMutation(alice(), mutationId) }
    }

    // ---- single rows ----

    @Test
    fun `a row is looked up by id for the account, tombstones included`() = runTest {
        val id = UUID.randomUUID()
        answer = { respondRows(listOf(RemoteTestData.gift(id, alice))) }

        val gift = reader.findGift(alice(), id)

        assertEquals(id, gift?.id)
        assertEquals(alice, gift?.ownerUserId)
        val request = requests.single()
        assertEquals("gifts", request.url.encodedPath.substringAfterLast('/'))
        assertEquals("eq.$id", request.url.parameters["id"])
        assertEquals("eq.$alice", request.url.parameters["owner_user_id"])
        assertEquals("2", request.url.parameters["limit"])
        assertTrue(request.url.parameters.names().none { it == "deleted_at" })
    }

    @Test
    fun `a row the account cannot see is reported as missing`() = runTest {
        answer = { respondRows(emptyList()) }

        assertNull(reader.findPerson(alice(), UUID.randomUUID()))
        assertNull(reader.findOccasion(alice(), UUID.randomUUID()))
        assertNull(reader.findGiftIdea(alice(), UUID.randomUUID()))
        assertNull(reader.findGift(alice(), UUID.randomUUID()))
        assertNull(reader.findWishlist(alice(), UUID.randomUUID()))
        assertNull(reader.findWish(alice(), UUID.randomUUID()))
    }

    @Test
    fun `a wishlist and a wish are looked up by id in their own tables, for the account`() = runTest {
        val id = UUID.randomUUID()
        answer = { request ->
            val table = request.url.encodedPath.substringAfterLast('/')
            respondRows(listOf(if (table == "wishlists") RemoteTestData.wishlist(id, alice) else RemoteTestData.wish(id, alice)))
        }

        assertEquals(id, reader.findWishlist(alice(), id)?.id)
        assertEquals(alice, reader.findWish(alice(), id)?.ownerUserId)

        assertEquals(listOf("wishlists", "wishes"), requests.map { it.url.encodedPath.substringAfterLast('/') })
        requests.forEach { request ->
            assertEquals("eq.$id", request.url.parameters["id"])
            assertEquals("eq.$alice", request.url.parameters["owner_user_id"])
        }
        answer = { respondRows(listOf(RemoteTestData.wish(id, bob))) }
        assertFailure(RemoteFailure.SERVER) { reader.findWish(alice(), id) }
    }

    @Test
    fun `a row lookup answered with another row, another owner or several rows is refused`() = runTest {
        val id = UUID.randomUUID()
        listOf(
            listOf(RemoteTestData.person(UUID.randomUUID(), alice)),
            listOf(RemoteTestData.person(id, bob)),
            listOf(RemoteTestData.person(id, alice), RemoteTestData.person(id, alice)),
        ).forEach { rows ->
            answer = { respondRows(rows) }
            assertFailure(RemoteFailure.SERVER) { reader.findPerson(alice(), id) }
        }
    }

    @Test
    fun `a gift is looked up by its source idea for the account, tombstones included`() = runTest {
        val id = UUID.randomUUID()
        val idea = UUID.randomUUID()
        answer = { respondRows(listOf(RemoteTestData.gift(id, alice, mapOf("source_gift_idea_id" to idea.toString(), "deleted_at" to "2026-10-14T08:00:00+00:00")))) }

        val gift = reader.findGiftBySourceIdea(alice(), idea)

        assertEquals(id, gift?.id)
        assertEquals(idea, gift?.sourceGiftIdeaId)
        val request = requests.single()
        assertEquals("gifts", request.url.encodedPath.substringAfterLast('/'))
        assertEquals("eq.$idea", request.url.parameters["source_gift_idea_id"])
        assertEquals("eq.$alice", request.url.parameters["owner_user_id"])
        assertEquals("2", request.url.parameters["limit"])
        assertTrue(request.url.parameters.names().none { it == "deleted_at" || it == "id" })
    }

    @Test
    fun `no gift made from the idea is reported as none`() = runTest {
        answer = { respondRows(emptyList()) }

        assertNull(reader.findGiftBySourceIdea(alice(), UUID.randomUUID()))
    }

    @Test
    fun `a source lookup answered with another idea's gift, another owner or several gifts is refused`() = runTest {
        val idea = UUID.randomUUID()
        val source = mapOf("source_gift_idea_id" to idea.toString())
        listOf(
            listOf(RemoteTestData.gift(UUID.randomUUID(), alice)),
            listOf(RemoteTestData.gift(UUID.randomUUID(), alice, mapOf("source_gift_idea_id" to null))),
            listOf(RemoteTestData.gift(UUID.randomUUID(), bob, source)),
            listOf(RemoteTestData.gift(UUID.randomUUID(), alice, source), RemoteTestData.gift(UUID.randomUUID(), alice, source)),
        ).forEach { rows ->
            answer = { respondRows(rows) }
            assertFailure(RemoteFailure.SERVER) { reader.findGiftBySourceIdea(alice(), idea) }
        }
    }

    // ---- accounts ----

    @Test
    fun `every request carries the captured account's token, never the publishable key`() = runTest {
        tables["people"] = listOf(RemoteTestData.person(UUID.randomUUID(), alice))

        reader.fetchPeople(alice())
        reader.findMutation(alice(), UUID.randomUUID())

        requests.forEach { request ->
            assertEquals("Bearer $alicesToken", request.headers[HttpHeaders.Authorization])
            assertEquals(1, request.headers.getAll(HttpHeaders.Authorization)?.size)
            assertEquals(PUBLISHABLE_KEY, request.headers["apikey"])
        }
    }

    @Test
    fun `a read started for one account stays that account's when another account is used meanwhile`() = runTest {
        val ids = (1..3).map { UUID.fromString("0000000$it-0000-4000-8000-000000000000") }
        tables["people"] = ids.map { RemoteTestData.person(it, alice) }
        val firstPageArrived = CompletableDeferred<Unit>()
        val accountSwitched = CompletableDeferred<Unit>()
        answer = { request ->
            val isAlicesFirstPage = request.url.parameters["owner_user_id"] == "eq.$alice" &&
                request.url.parameters["id"] == null
            if (isAlicesFirstPage) {
                firstPageArrived.complete(Unit)
                accountSwitched.await()
            }
            if (request.url.encodedPath.endsWith("sync_mutations")) respondRows(emptyList()) else serve(request)
        }

        val alicesRead = async { reader.fetchPeople(alice()) }
        firstPageArrived.await()
        // Bob is now the signed-in account, and works with the backend while Alice's read is still
        // in progress; the reader never looks at the global session, only at captured accounts.
        reader.findMutation(RemoteAccount.capture(bob, bobsToken), UUID.randomUUID())
        accountSwitched.complete(Unit)
        val people = alicesRead.await()

        assertEquals(ids, people.map { it.id })
        val (alicesRequests, bobsRequests) = requests.partition { it.url.parameters["owner_user_id"] == "eq.$alice" }
        // Two rows, one row, then the empty page that ends the read.
        assertEquals(3, alicesRequests.size)
        assertTrue(alicesRequests.all { it.headers[HttpHeaders.Authorization] == "Bearer $alicesToken" })
        assertEquals(listOf("Bearer $bobsToken"), bobsRequests.map { it.headers[HttpHeaders.Authorization] })
        // Alice's later pages were sent after the switch, still as Alice.
        assertTrue(requests.indexOf(alicesRequests.last()) > requests.indexOf(bobsRequests.single()))
    }

    // ---- failures ----

    @Test
    fun `server answers are reported as typed failures`() = runTest {
        mapOf(
            HttpStatusCode.Unauthorized to RemoteFailure.UNAUTHENTICATED,
            HttpStatusCode.Forbidden to RemoteFailure.FORBIDDEN,
            HttpStatusCode.InternalServerError to RemoteFailure.SERVER,
            HttpStatusCode.ServiceUnavailable to RemoteFailure.SERVER,
            HttpStatusCode.BadRequest to RemoteFailure.SERVER,
        ).forEach { (status, failure) ->
            answer = { respondError(status) }
            assertFailure(failure) { reader.fetchPeople(alice()) }
        }
    }

    @Test
    fun `a request that never got an answer is a network failure`() = runTest {
        answer = { throw IOException("connection reset") }

        assertFailure(RemoteFailure.NETWORK) { reader.findMutation(alice(), UUID.randomUUID()) }
    }

    @Test
    fun `an unreadable response is a server failure`() = runTest {
        answer = { respond("""[{"id": "not-a-row"}]""", HttpStatusCode.OK, JSON_HEADERS) }

        assertFailure(RemoteFailure.SERVER) { reader.fetchPeople(alice()) }
    }

    @Test
    fun `the token appears in no failure`() = runTest {
        val secretToken = token(alice, secret = "top-secret-signature")
        answer = { respondError(HttpStatusCode.Unauthorized) }

        val failure = assertFailure(RemoteFailure.UNAUTHENTICATED) {
            reader.fetchPeople(RemoteAccount.capture(alice, secretToken))
        }

        generateSequence<Throwable>(failure) { it.cause }.forEach { throwable ->
            assertFalse(throwable.toString().contains("top-secret-signature"))
            assertFalse(throwable.stackTraceToString().contains(secretToken))
        }
    }

    // ---- fake server ----

    private fun alice() = RemoteAccount.capture(alice, alicesToken)

    /** Answers like PostgREST: owner filter, `id > after`, ordered by id (uuid text order), limited. */
    private fun MockRequestHandleScope.serve(request: HttpRequestData, cap: Int = Int.MAX_VALUE): HttpResponseData {
        val table = request.url.encodedPath.substringAfterLast('/')
        val owner = request.url.parameters["owner_user_id"]!!.removePrefix("eq.")
        val after = request.url.parameters["id"]?.removePrefix("gt.")
        val limit = minOf(request.url.parameters["limit"]!!.toInt(), cap)
        val rows = tables[table].orEmpty()
            .filter { it.text("owner_user_id") == owner && (after == null || it.text("id") > after) }
            .sortedBy { it.text("id") }
            .take(limit)
        return respondRows(rows)
    }

    private fun MockRequestHandleScope.respondRows(rows: List<JsonObject>) =
        respond(JsonArray(rows).toString(), HttpStatusCode.OK, JSON_HEADERS)

    private fun MockRequestHandleScope.respondError(status: HttpStatusCode) = respond(
        """{"code":"PGRST301","message":"error ${status.value}","details":null,"hint":null}""",
        status,
        JSON_HEADERS,
    )

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private suspend fun assertFailure(expected: RemoteFailure, block: suspend () -> Unit): RemoteException {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is RemoteException)
        assertEquals(expected, (failure as RemoteException).failure)
        return failure
    }

    private companion object {
        const val URL = "https://test.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_test-key"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
