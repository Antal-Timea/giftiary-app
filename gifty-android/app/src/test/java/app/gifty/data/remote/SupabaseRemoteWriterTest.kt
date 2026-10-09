package app.gifty.data.remote

import app.gifty.data.remote.RemoteTestData.alice
import app.gifty.data.remote.RemoteTestData.bob
import app.gifty.data.remote.RemoteTestData.token
import app.gifty.data.remote.dto.RemoteGiftFields
import app.gifty.data.remote.dto.RemoteGiftIdeaFields
import app.gifty.data.remote.dto.RemoteOccasionFields
import app.gifty.data.remote.dto.RemotePersonFields
import app.gifty.domain.model.GiftDirection
import app.gifty.domain.model.GiftIdeaStatus
import app.gifty.domain.model.OccasionType
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.util.Collections
import java.util.UUID

/**
 * The writer against a fake PostgREST that stores rows, applies the version filter, sets the
 * server-controlled columns like the real triggers do, and returns the written rows.
 */
@RunWith(RobolectricTestRunner::class)
class SupabaseRemoteWriterTest {

    private val alicesToken = token(alice)
    private val bobsToken = token(bob)
    private val requests: MutableList<Request> = Collections.synchronizedList(mutableListOf())
    private val stored = Collections.synchronizedMap(mutableMapOf<String, JsonObject>())
    private var answer: suspend MockRequestHandleScope.(Request) -> HttpResponseData = { serve(it) }

    private val engine = MockEngine { data ->
        val request = Request(data, data.body.toByteArray().decodeToString())
        requests += request
        answer(request)
    }
    private val writer = SupabaseRemoteWriter(AccountBoundClients(URL, PUBLISHABLE_KEY, engine))

    private val personId = UUID.fromString("10000000-0000-4000-8000-000000000001")
    private val occasionId = UUID.fromString("20000000-0000-4000-8000-000000000002")
    private val ideaId = UUID.fromString("30000000-0000-4000-8000-000000000003")
    private val giftId = UUID.fromString("40000000-0000-4000-8000-000000000004")
    private val m1 = UUID.fromString("c1000000-0000-4000-8000-000000000001")
    private val m2 = UUID.fromString("c2000000-0000-4000-8000-000000000002")
    // Mutation ids are single-use across all tables, so each write of a test needs its own.
    private val mutations = List(4) { UUID.fromString("c${it + 3}000000-0000-4000-8000-00000000000${it + 3}") }

    @After
    fun tearDown() {
        engine.close()
    }

    // ---- inserts ----

    @Test
    fun `an insert posts exactly the row's id, the captured owner, its fields and the mutation id`() = runTest {
        val inserts = listOf(
            WriteCase("people", PERSON_FIELDS) { writer.insertPerson(alice(), personId, mutations[0], person()) },
            WriteCase("occasions", OCCASION_FIELDS) { writer.insertOccasion(alice(), occasionId, mutations[1], occasion()) },
            WriteCase("gift_ideas", IDEA_FIELDS) { writer.insertGiftIdea(alice(), ideaId, mutations[2], idea()) },
            WriteCase("gifts", GIFT_FIELDS) { writer.insertGift(alice(), giftId, mutations[3], gift()) },
        )
        val ids = listOf(personId, occasionId, ideaId, giftId)

        inserts.forEachIndexed { index, (table, fields, insert) ->
            assertEquals(Committed(1), insert())

            val request = requests.last()
            assertEquals(HttpMethod.Post, request.data.method)
            assertEquals(table, request.table)
            assertTrue(request.prefer.contains("return=representation"))
            assertFalse("never an upsert", request.prefer.contains("resolution"))
            assertTrue(request.data.url.parameters.names().none { it == "on_conflict" })
            val row = request.json.jsonArray.single().jsonObject
            assertEquals(fields + setOf("id", "owner_user_id", "last_mutation_id"), row.keys)
            assertEquals(ids[index].toString(), row.text("id"))
            assertEquals(alice.toString(), row.text("owner_user_id"))
            assertEquals(mutations[index].toString(), row.text("last_mutation_id"))
        }
    }

    @Test
    fun `server-controlled and local-only columns are never sent`() = runTest {
        writer.insertGift(alice(), giftId, m1, gift())
        writer.updateGift(alice(), giftId, expectedVersion = 1, mutationId = m2, fields = gift())

        requests.forEach { request ->
            val keys = (request.json as? JsonArray)?.single()?.jsonObject?.keys ?: request.json.jsonObject.keys
            listOf("version", "created_at", "updated_at", "remote_state", "remoteState").forEach { column ->
                assertFalse("$column sent", column in keys)
            }
        }
    }

    @Test
    fun `values travel exactly, with large amounts, dates, instants and explicit nulls`() = runTest {
        writer.insertGift(alice(), giftId, m1, gift().copy(priceAmountMinor = 9_007_199_254_740_993L))

        val row = requests.single().json.jsonArray.single().jsonObject
        // 2^53 + 1, which a Double would turn into 2^53.
        assertEquals("9007199254740993", row.getValue("price_amount_minor").jsonPrimitive.content)
        assertEquals("2024-02-29", row.text("given_at"))
        assertEquals("2026-10-05T08:00:00.123Z", row.text("deleted_at"))
        // A null is sent as null, so the column is cleared rather than left as it was.
        assertEquals(JsonNull, row.getValue("giver_person_id"))
        assertEquals(JsonNull, row.getValue("occasion_id"))
    }

    // ---- conditional updates ----

    @Test
    fun `an update is a compare-and-set on id, owner and version, and returns the new version`() = runTest {
        writer.insertPerson(alice(), personId, mutations[0], person())
        writer.insertOccasion(alice(), occasionId, mutations[1], occasion())
        writer.insertGiftIdea(alice(), ideaId, mutations[2], idea())
        writer.insertGift(alice(), giftId, mutations[3], gift())
        requests.clear()
        val updateMutations = List(4) { UUID.randomUUID() }

        val updates = listOf(
            WriteCase("people", PERSON_FIELDS) {
                writer.updatePerson(alice(), personId, 1, updateMutations[0], person().copy(name = "Anna K."))
            },
            WriteCase("occasions", OCCASION_FIELDS) { writer.updateOccasion(alice(), occasionId, 1, updateMutations[1], occasion()) },
            WriteCase("gift_ideas", IDEA_FIELDS) { writer.updateGiftIdea(alice(), ideaId, 1, updateMutations[2], idea()) },
            WriteCase("gifts", GIFT_FIELDS) { writer.updateGift(alice(), giftId, 1, updateMutations[3], gift()) },
        )
        val ids = listOf(personId, occasionId, ideaId, giftId)

        updates.forEachIndexed { index, (table, fields, update) ->
            assertEquals(Committed(2), update())

            val request = requests.last()
            assertEquals(HttpMethod.Patch, request.data.method)
            assertEquals(table, request.table)
            assertEquals("eq.${ids[index]}", request.data.url.parameters["id"])
            assertEquals("eq.$alice", request.data.url.parameters["owner_user_id"])
            assertEquals("eq.1", request.data.url.parameters["version"])
            assertTrue(request.prefer.contains("return=representation"))
            // The whole client-decided state, and nothing that identifies or versions the row.
            assertEquals(fields + "last_mutation_id", request.json.jsonObject.keys)
            assertEquals(updateMutations[index].toString(), request.json.jsonObject.text("last_mutation_id"))
        }
    }

    @Test
    fun `an update that matches no row is reported as no match, not as success or conflict`() = runTest {
        writer.insertPerson(alice(), personId, m1, person())
        writer.updatePerson(alice(), personId, 1, m2, person())

        // Stale expected version, an unknown row, and a row that is not the account's.
        assertEquals(NoMatch, writer.updatePerson(alice(), personId, 1, UUID.randomUUID(), person()))
        assertEquals(NoMatch, writer.updatePerson(alice(), UUID.randomUUID(), 1, UUID.randomUUID(), person()))
        assertEquals(NoMatch, writer.updatePerson(account(bob, bobsToken), personId, 2, UUID.randomUUID(), person()))
    }

    @Test
    fun `deleting is an update that sets the tombstone, and an idea can be archived`() = runTest {
        writer.insertPerson(alice(), personId, mutations[0], person())
        writer.insertGiftIdea(alice(), ideaId, mutations[1], idea())
        val deletedAt = Instant.parse("2026-10-09T10:00:00.250Z")
        val archivedAt = Instant.parse("2026-10-09T11:00:00Z")

        writer.updatePerson(alice(), personId, 1, mutations[2], person().copy(deletedAt = deletedAt))
        writer.updateGiftIdea(alice(), ideaId, 1, mutations[3], idea().copy(archivedAt = archivedAt))

        assertEquals("2026-10-09T10:00:00.250Z", requests[2].json.jsonObject.text("deleted_at"))
        assertEquals("Anna", requests[2].json.jsonObject.text("name"))
        assertEquals("2026-10-09T11:00:00Z", requests[3].json.jsonObject.text("archived_at"))
        assertTrue(requests.none { it.data.method == HttpMethod.Delete })
    }

    // ---- what counts as proof ----

    @Test
    fun `a response that does not show this write is not taken as success`() = runTest {
        // The returned row carries another mutation id, so it is not proof that this write happened.
        answer = { request ->
            val row = request.json.jsonArray.single().jsonObject.with("last_mutation_id", m2).complete(version = 1)
            respondRows(listOf(row))
        }
        assertFailure(RemoteFailure.SERVER) { writer.insertPerson(alice(), personId, m1, person()) }

        answer = { respondRows(emptyList()) }
        assertFailure(RemoteFailure.SERVER) { writer.insertPerson(alice(), personId, m1, person()) }

        answer = { request ->
            val row = request.json.jsonObject.with("id", personId).with("owner_user_id", alice).complete(version = 2)
            respondRows(listOf(row, row))
        }
        assertFailure(RemoteFailure.SERVER) { writer.updatePerson(alice(), personId, 1, m1, person()) }

        answer = { respond("[{\"id\":1}]", HttpStatusCode.Created, JSON_HEADERS) }
        assertFailure(RemoteFailure.SERVER) { writer.insertPerson(alice(), personId, m1, person()) }
    }

    // ---- accounts ----

    @Test
    fun `a write started for one account stays that account's when another account writes meanwhile`() = runTest {
        val alicesWriteArrived = CompletableDeferred<Unit>()
        val accountSwitched = CompletableDeferred<Unit>()
        answer = { request ->
            if (request.data.headers[HttpHeaders.Authorization] == "Bearer $alicesToken") {
                alicesWriteArrived.complete(Unit)
                accountSwitched.await()
            }
            serve(request)
        }

        val alicesWrite = async { writer.insertPerson(alice(), personId, m1, person()) }
        alicesWriteArrived.await()
        // Bob is now the signed-in account and writes while Alice's request is still open.
        val bobsPerson = UUID.randomUUID()
        writer.insertPerson(account(bob, bobsToken), bobsPerson, m2, person())
        accountSwitched.complete(Unit)

        assertEquals(Committed(1), alicesWrite.await())
        val alicesRequest = requests.single { it.json.jsonArray.single().jsonObject.text("id") == personId.toString() }
        assertEquals("Bearer $alicesToken", alicesRequest.data.headers[HttpHeaders.Authorization])
        assertEquals(alice.toString(), alicesRequest.json.jsonArray.single().jsonObject.text("owner_user_id"))
        val bobsRequest = requests.single { it.json.jsonArray.single().jsonObject.text("id") == bobsPerson.toString() }
        assertEquals("Bearer $bobsToken", bobsRequest.data.headers[HttpHeaders.Authorization])
        assertEquals(bob.toString(), bobsRequest.json.jsonArray.single().jsonObject.text("owner_user_id"))
    }

    @Test
    fun `an update filters on the captured owner and carries its token`() = runTest {
        writer.updateGift(account(bob, bobsToken), giftId, 4, m1, gift())

        val request = requests.single()
        assertEquals("eq.$bob", request.data.url.parameters["owner_user_id"])
        assertEquals("Bearer $bobsToken", request.data.headers[HttpHeaders.Authorization])
        assertEquals(1, request.data.headers.getAll(HttpHeaders.Authorization)?.size)
    }

    // ---- failures ----

    @Test
    fun `server errors are classified by their PostgreSQL code`() = runTest {
        listOf(
            Triple(HttpStatusCode.Unauthorized, "PGRST301", RemoteFailure.UNAUTHENTICATED),
            Triple(HttpStatusCode.Forbidden, "42501", RemoteFailure.FORBIDDEN),
            Triple(HttpStatusCode.Conflict, "23505", RemoteFailure.DUPLICATE),
            Triple(HttpStatusCode.BadRequest, "23514", RemoteFailure.CONSTRAINT_VIOLATION),
            Triple(HttpStatusCode.Conflict, "23503", RemoteFailure.CONSTRAINT_VIOLATION),
            Triple(HttpStatusCode.BadRequest, "23502", RemoteFailure.CONSTRAINT_VIOLATION),
            Triple(HttpStatusCode.BadRequest, "22P02", RemoteFailure.CONSTRAINT_VIOLATION),
            Triple(HttpStatusCode.InternalServerError, "XX000", RemoteFailure.SERVER),
        ).forEach { (status, code, failure) ->
            answer = { respondError(status, code) }
            assertFailure(failure) { writer.insertGiftIdea(alice(), ideaId, m1, idea()) }
            assertFailure(failure) { writer.updateGiftIdea(alice(), ideaId, 1, m1, idea()) }
        }
    }

    @Test
    fun `reusing a mutation id fails as a duplicate, and nothing is retried`() = runTest {
        writer.insertPerson(alice(), personId, m1, person())

        assertFailure(RemoteFailure.DUPLICATE) { writer.insertPerson(alice(), personId, m1, person()) }
        assertFailure(RemoteFailure.DUPLICATE) { writer.updatePerson(alice(), personId, 1, m1, person()) }

        assertEquals(3, requests.size)
        assertEquals(1L, stored.getValue(personId.toString()).getValue("version").jsonPrimitive.long)
    }

    @Test
    fun `no answer, or a timeout after sending, is a network failure, not a failed write`() = runTest {
        answer = { throw IOException("connection reset") }
        assertFailure(RemoteFailure.NETWORK) { writer.insertPerson(alice(), personId, m1, person()) }

        answer = { request -> throw HttpRequestTimeoutException(request.data.url.toString(), 10_000L) }
        assertFailure(RemoteFailure.NETWORK) { writer.updatePerson(alice(), personId, 1, m1, person()) }

        assertEquals(2, requests.size)
    }

    @Test
    fun `cancelling a write passes the cancellation through`() = runTest {
        val arrived = CompletableDeferred<Unit>()
        answer = {
            arrived.complete(Unit)
            awaitCancellation()
        }

        val write = async { writer.insertPerson(alice(), personId, m1, person()) }
        arrived.await()
        write.cancel()

        val failure = runCatching { write.await() }.exceptionOrNull()
        assertTrue("got $failure", failure is CancellationException)
        assertFalse(failure is RemoteException)
    }

    @Test
    fun `the token appears in no write failure`() = runTest {
        val secretToken = token(alice, secret = "top-secret-signature")
        answer = { respondError(HttpStatusCode.Conflict, "23505") }

        val failure = assertFailure(RemoteFailure.DUPLICATE) {
            writer.insertPerson(account(alice, secretToken), personId, m1, person())
        }

        generateSequence<Throwable>(failure) { it.cause }.forEach { throwable ->
            assertFalse(throwable.stackTraceToString().contains("top-secret-signature"))
        }
    }

    // ---- fixtures ----

    private fun alice() = account(alice, alicesToken)

    private fun account(owner: UUID, token: String) = RemoteAccount.capture(owner, token)

    private fun person() = RemotePersonFields(
        name = "Anna", relationship = "Sister", photoPath = null, linkedUserId = null, deletedAt = null,
    )

    private fun occasion() = RemoteOccasionFields(
        personId = personId, type = OccasionType.BIRTHDAY, title = null, day = 29, month = 2, year = null,
        repeatsYearly = true, deletedAt = null,
    )

    private fun idea() = RemoteGiftIdeaFields(
        recipientPersonId = personId, occasionId = null, name = "Scarf", status = GiftIdeaStatus.PURCHASED,
        estimatedPriceAmountMinor = 1_299_000, estimatedPriceCurrencyCode = "HUF", actualPriceAmountMinor = null,
        actualPriceCurrencyCode = null, photoPath = null, productUrl = null, note = null, archivedAt = null, deletedAt = null,
    )

    private fun gift() = RemoteGiftFields(
        direction = GiftDirection.GIVEN, giverPersonId = null, recipientPersonId = personId, occasionId = null,
        sourceGiftIdeaId = ideaId, name = "Scarf", givenAt = LocalDate.of(2024, 2, 29), priceAmountMinor = 9900,
        priceCurrencyCode = "EUR", photoPath = null, productUrl = null, note = "Green",
        deletedAt = Instant.parse("2026-10-05T08:00:00.123Z"),
    )

    private class WriteCase(val table: String, val fields: Set<String>, val write: suspend () -> Any) {
        operator fun component1() = table
        operator fun component2() = fields
        operator fun component3() = write
    }

    // ---- fake server ----

    private class Request(val data: HttpRequestData, body: String) {
        val json = Json.parseToJsonElement(body)
        val table = data.url.encodedPath.substringAfterLast('/')
        val prefer = data.headers.getAll("Prefer").orEmpty().joinToString(",")
    }

    /** Inserts reject a taken id or mutation id; updates apply only on id, owner and version. */
    private fun MockRequestHandleScope.serve(request: Request): HttpResponseData = when (request.data.method) {
        HttpMethod.Post -> {
            val row = request.json.jsonArray.single().jsonObject
            if (row.text("id") in stored || usedMutation(row.text("last_mutation_id"))) {
                respondError(HttpStatusCode.Conflict, "23505")
            } else {
                val written = row.complete(version = 1)
                stored[row.text("id")] = written
                respondRows(listOf(written))
            }
        }
        HttpMethod.Patch -> {
            val params = request.data.url.parameters
            val current = stored[params["id"]!!.removePrefix("eq.")]
            val matches = current != null &&
                current.text("owner_user_id") == params["owner_user_id"]!!.removePrefix("eq.") &&
                current.getValue("version").jsonPrimitive.long.toString() == params["version"]!!.removePrefix("eq.")
            when {
                !matches -> respondRows(emptyList())
                usedMutation(request.json.jsonObject.text("last_mutation_id")) -> respondError(HttpStatusCode.Conflict, "23505")
                else -> {
                    val version = current!!.getValue("version").jsonPrimitive.long + 1
                    val written = JsonObject(current + request.json.jsonObject + ("version" to JsonPrimitive(version)))
                    stored[current.text("id")] = written
                    respondRows(listOf(written))
                }
            }
        }
        else -> error("Unexpected ${request.data.method}")
    }

    private fun usedMutation(mutationId: String) = stored.values.any { it.text("last_mutation_id") == mutationId }

    private fun JsonObject.complete(version: Long) = JsonObject(
        this + mapOf(
            "version" to JsonPrimitive(version),
            "created_at" to JsonPrimitive("2026-10-09T09:00:00.123456+00:00"),
            "updated_at" to JsonPrimitive("2026-10-09T09:00:00.123456+00:00"),
        ),
    )

    private fun JsonObject.with(key: String, value: UUID) = JsonObject(this + (key to JsonPrimitive(value.toString())))

    private fun JsonObject.text(key: String) = getValue(key).jsonPrimitive.content

    private fun MockRequestHandleScope.respondRows(rows: List<JsonObject>) =
        respond(JsonArray(rows).toString(), HttpStatusCode.OK, JSON_HEADERS)

    private fun MockRequestHandleScope.respondError(status: HttpStatusCode, code: String) = respond(
        """{"code":"$code","message":"rejected","details":null,"hint":null}""",
        status,
        JSON_HEADERS,
    )

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

        val PERSON_FIELDS = setOf("name", "relationship", "photo_path", "linked_user_id", "deleted_at")
        val OCCASION_FIELDS = setOf("person_id", "type", "title", "day", "month", "year", "repeats_yearly", "deleted_at")
        val IDEA_FIELDS = setOf(
            "recipient_person_id", "occasion_id", "name", "status", "estimated_price_amount_minor",
            "estimated_price_currency_code", "actual_price_amount_minor", "actual_price_currency_code", "photo_path",
            "product_url", "note", "archived_at", "deleted_at",
        )
        val GIFT_FIELDS = setOf(
            "direction", "giver_person_id", "recipient_person_id", "occasion_id", "source_gift_idea_id", "name",
            "given_at", "price_amount_minor", "price_currency_code", "photo_path", "product_url", "note", "deleted_at",
        )
    }
}
