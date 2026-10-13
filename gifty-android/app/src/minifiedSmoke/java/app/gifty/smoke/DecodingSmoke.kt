package app.gifty.smoke

import app.gifty.data.remote.AccountBoundClients
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.SupabaseRemoteReader
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import java.util.Base64
import java.util.UUID

/**
 * Reads one row of each synced table through the production reader, in whatever form R8 left it,
 * from PostgREST-shaped responses served by a mock engine. Exists only in the minifiedSmoke build,
 * for [MinifiedDecodingSmoke].
 *
 * Returns the reader's runtime class name, then each table's decoded rows, one line each.
 */
object DecodingSmoke {

    fun run(): String = runBlocking {
        val engine = MockEngine { request ->
            // One page with the row, then the empty page that ends the read.
            val table = request.url.encodedPath.substringAfterLast('/')
            val body = if (request.url.parameters["id"] == null) ROWS.getValue(table) else "[]"
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val reader = SupabaseRemoteReader(AccountBoundClients("https://smoke.invalid", "smoke-key", engine))
        val account = RemoteAccount.capture(OWNER, token(OWNER))
        listOf(
            reader.javaClass.name,
            reader.fetchPeople(account).toString(),
            reader.fetchOccasions(account).toString(),
            reader.fetchGiftIdeas(account).toString(),
            reader.fetchGifts(account).toString(),
        ).joinToString("\n")
    }

    private val OWNER: UUID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")

    /** An unsigned JWT for [subject]; only the server would verify the signature. */
    private fun token(subject: UUID): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payload = encoder.encodeToString("""{"sub":"$subject","role":"authenticated"}""".toByteArray())
        return "$header.$payload.smoke"
    }

    // Rows as PostgREST returns them: snake_case, every column, timestamps with offsets and microseconds.
    private val ROWS = mapOf(
        "people" to """[{"id":"a0000000-0000-4000-8000-00000000000a","owner_user_id":"$OWNER","name":"Anna",""" +
            """"relationship":"Sister","photo_path":null,"linked_user_id":null,"version":3,""" +
            """"created_at":"2026-10-01T10:00:00.123456+00:00","updated_at":"2026-10-02T11:30:00+00:00",""" +
            """"deleted_at":null,"last_mutation_id":"c0000000-0000-4000-8000-000000000001"}]""",
        "occasions" to """[{"id":"e0000000-0000-4000-8000-00000000000e","owner_user_id":"$OWNER",""" +
            """"person_id":"a0000000-0000-4000-8000-00000000000a","type":"BIRTHDAY","title":null,"day":29,""" +
            """"month":2,"year":null,"repeats_yearly":true,"version":1,"created_at":"2026-10-01T10:00:00+00:00",""" +
            """"updated_at":"2026-10-01T10:00:00+00:00","deleted_at":null,"last_mutation_id":null}]""",
        "gift_ideas" to """[{"id":"d0000000-0000-4000-8000-00000000000d","owner_user_id":"$OWNER",""" +
            """"recipient_person_id":"a0000000-0000-4000-8000-00000000000a",""" +
            """"occasion_id":"e0000000-0000-4000-8000-00000000000e","name":"Scarf","status":"PURCHASED",""" +
            """"estimated_price_amount_minor":1299000,"estimated_price_currency_code":"HUF",""" +
            """"actual_price_amount_minor":null,"actual_price_currency_code":null,"photo_path":null,""" +
            """"product_url":"https://example.com/scarf","note":null,"version":4,""" +
            """"created_at":"2026-10-01T10:00:00.5+00:00","updated_at":"2026-10-03T09:00:00+00:00",""" +
            """"archived_at":"2026-10-03T09:00:00.000999+00:00","deleted_at":null,""" +
            """"last_mutation_id":"c0000000-0000-4000-8000-000000000004"}]""",
        "gifts" to """[{"id":"f0000000-0000-4000-8000-00000000000f","owner_user_id":"$OWNER","direction":"GIVEN",""" +
            """"giver_person_id":null,"recipient_person_id":"a0000000-0000-4000-8000-00000000000a",""" +
            """"occasion_id":null,"source_gift_idea_id":"d0000000-0000-4000-8000-00000000000d","name":"Scarf",""" +
            """"given_at":"2024-02-29","price_amount_minor":1999,"price_currency_code":"EUR","photo_path":null,""" +
            """"product_url":null,"note":"Green","version":2,"created_at":"2026-10-01T10:00:00+00:00",""" +
            """"updated_at":"2026-10-04T12:00:00+02:00","deleted_at":"2026-10-04T12:00:00+02:00",""" +
            """"last_mutation_id":"c0000000-0000-4000-8000-000000000005"}]""",
    )
}
