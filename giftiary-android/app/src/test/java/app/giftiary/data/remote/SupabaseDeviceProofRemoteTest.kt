package app.giftiary.data.remote

import app.giftiary.data.remote.RemoteTestData.alice
import app.giftiary.data.remote.RemoteTestData.bob
import app.giftiary.data.remote.RemoteTestData.token
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** The device proof calls against a fake PostgREST: who they are made as, and which answers count. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseDeviceProofRemoteTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) }
    private val engine = MockEngine { request ->
        requests += request
        answer(request)
    }

    private val auth: Auth = createSupabaseClient(URL, PUBLISHABLE_KEY) {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private val remote = SupabaseDeviceProofRemote(AccountBoundClients(URL, PUBLISHABLE_KEY, engine), RemoteAccounts(auth))

    @Before
    fun signIn() = runTest {
        auth.importSession(
            UserSession(token(alice), "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = alice.toString())),
            autoRefresh = false,
            source = SessionSource.Storage,
        )
    }

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun `a proof is registered as the account itself, by its hash only`() = runTest {
        remote.register(alice, HASH)

        val request = requests.single()
        assertEquals("/rest/v1/rpc/register_device_proof", request.url.encodedPath)
        assertEquals(Json.parseToJsonElement("""{"p_proof_hash":"$HASH"}"""), bodyOf(request))
        assertEquals("Bearer ${token(alice)}", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `nothing is registered for an account the session does not belong to`() = runTest {
        assertFailure(RemoteFailure.ACCOUNT_MISMATCH) { remote.register(bob, HASH) }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `the question goes without any account's token, even while one is signed in`() = runTest {
        answer = { respond("true", HttpStatusCode.OK, JSON_HEADERS) }

        assertTrue(remote.isLive(SECRET))

        val request = requests.single()
        assertEquals("/rest/v1/rpc/device_proof_is_live", request.url.encodedPath)
        assertEquals(Json.parseToJsonElement("""{"p_proof":"$SECRET"}"""), bodyOf(request))
        assertFalse(request.headers[HttpHeaders.Authorization].orEmpty().contains(token(alice)))
        assertEquals(PUBLISHABLE_KEY, request.headers["apikey"])
    }

    @Test
    fun `only the literal false says the account is gone`() = runTest {
        answer = { respond("false", HttpStatusCode.OK, JSON_HEADERS) }
        assertFalse(remote.isLive(SECRET))
        answer = { respond(" false\n", HttpStatusCode.OK, JSON_HEADERS) }
        assertFalse(remote.isLive(SECRET))
    }

    @Test
    fun `an unexpected answer is a failure, never false`() = runTest {
        for (body in listOf("", "null", "\"false\"", "0", "[false]", "{\"device_proof_is_live\":false}", "False", "f")) {
            answer = { respond(body, HttpStatusCode.OK, JSON_HEADERS) }
            assertFailure(RemoteFailure.SERVER) { remote.isLive(SECRET) }
        }
    }

    @Test
    fun `refusals, server errors and lost answers are failures, never false`() = runTest {
        val refusals = mapOf(
            HttpStatusCode.Unauthorized to RemoteFailure.UNAUTHENTICATED,
            HttpStatusCode.NotFound to RemoteFailure.SERVER,
            HttpStatusCode.RequestTimeout to RemoteFailure.SERVER,
            HttpStatusCode.TooManyRequests to RemoteFailure.SERVER,
            HttpStatusCode.InternalServerError to RemoteFailure.SERVER,
            HttpStatusCode.ServiceUnavailable to RemoteFailure.SERVER,
        )
        for ((status, failure) in refusals) {
            answer = { respond("""{"code":"PGRST000","message":"false","details":null,"hint":null}""", status, JSON_HEADERS) }
            assertFailure(failure) { remote.isLive(SECRET) }
        }
        // The function not deployed yet, as on a backend without the migration.
        answer = { respond("""{"code":"PGRST202","message":"not found","details":null,"hint":null}""", HttpStatusCode.NotFound, JSON_HEADERS) }
        assertFailure(RemoteFailure.SERVER) { remote.isLive(SECRET) }
        answer = { respond("""{"code":"22023","message":"invalid proof","details":null,"hint":null}""", HttpStatusCode.BadRequest, JSON_HEADERS) }
        assertFailure(RemoteFailure.CONSTRAINT_VIOLATION) { remote.isLive(SECRET) }

        answer = { throw IOException("offline") }
        assertFailure(RemoteFailure.NETWORK) { remote.isLive(SECRET) }
    }

    private fun bodyOf(request: HttpRequestData) =
        Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())

    private suspend fun assertFailure(expected: RemoteFailure, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is RemoteException)
        assertEquals(expected, (failure as RemoteException).failure)
    }

    private companion object {
        const val URL = "https://test.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_test-key"
        val SECRET = "a1".repeat(32)
        val HASH = "b2".repeat(32)
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
