package app.giftiary.data.remote

import app.giftiary.data.remote.RemoteTestData.alice
import app.giftiary.data.remote.RemoteTestData.token
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** Gift photo objects against a fake Storage API: the requests made, and how failures are read. */
@RunWith(RobolectricTestRunner::class)
class SupabaseGiftPhotoRemoteTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = {
        respond("""{"Key":"gift-photos/$PATH","Id":"x"}""", HttpStatusCode.OK, JSON_HEADERS)
    }
    private val engine = MockEngine { request ->
        requests += request
        answer(request)
    }
    private val remote = SupabaseGiftPhotoRemote(AccountBoundClients(URL, PUBLISHABLE_KEY, engine))
    private val account = RemoteAccount.capture(alice, token(alice))

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun `an upload puts the JPEG at its path in the private bucket as the account, replacing any earlier one`() = runTest {
        remote.upload(account, PATH, BYTES)

        val request = requests.single()
        assertEquals("/storage/v1/object/gift-photos/$PATH", request.url.encodedPath)
        assertEquals("true", request.headers["x-upsert"])
        assertEquals("Bearer ${token(alice)}", request.headers[HttpHeaders.Authorization])
        assertEquals("image/jpeg", request.body.contentType.toString())
        assertArrayEquals(BYTES, request.body.toByteArray())
    }

    @Test
    fun `a download is authenticated, never through a public URL`() = runTest {
        answer = { respond(BYTES, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "image/jpeg")) }

        val bytes = remote.download(account, PATH)

        assertArrayEquals(BYTES, bytes)
        assertEquals("/storage/v1/object/authenticated/gift-photos/$PATH", requests.single().url.encodedPath)
        assertEquals("Bearer ${token(alice)}", requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun `delete names exactly the given paths, and nothing is sent for none`() = runTest {
        answer = { respond("[]", HttpStatusCode.OK, JSON_HEADERS) }

        remote.delete(account, emptyList())
        assertTrue(requests.isEmpty())

        remote.delete(account, listOf(PATH))
        val request = requests.single()
        assertEquals("DELETE /storage/v1/object/gift-photos", "${request.method.value} ${request.url.encodedPath}")
        assertEquals(
            Json.parseToJsonElement("""{"prefixes":["$PATH"]}"""),
            Json.parseToJsonElement(request.body.toByteArray().decodeToString()),
        )
    }

    @Test
    fun `refusals and lost answers are told apart`() = runTest {
        answer = { respond("""{"statusCode":"403","error":"Unauthorized","message":"policy"}""", HttpStatusCode.Forbidden, JSON_HEADERS) }
        assertFailure(RemoteFailure.FORBIDDEN) { remote.upload(account, PATH, BYTES) }

        answer = { respond("""{"statusCode":"401","error":"Unauthorized","message":"jwt"}""", HttpStatusCode.Unauthorized, JSON_HEADERS) }
        assertFailure(RemoteFailure.UNAUTHENTICATED) { remote.upload(account, PATH, BYTES) }

        answer = { throw IOException("offline") }
        assertFailure(RemoteFailure.NETWORK) { remote.upload(account, PATH, BYTES) }
    }

    private suspend fun assertFailure(expected: RemoteFailure, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is RemoteException)
        assertEquals(expected, (failure as RemoteException).failure)
    }

    private companion object {
        const val URL = "https://test.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_test-key"
        val PATH = "$alice/10000000-0000-4000-8000-000000000001.jpg"
        val BYTES = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
