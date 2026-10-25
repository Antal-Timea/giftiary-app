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
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

/** The deletion RPC against a fake PostgREST: what is sent, and how refusals are read. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseAccountDeletionRemoteTest {

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
    private val remote = SupabaseAccountDeletionRemote(AccountBoundClients(URL, PUBLISHABLE_KEY, engine), RemoteAccounts(auth))

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
    fun `the account is deleted by its own token, naming nobody`() = runTest {
        remote.deleteAccount(alice)

        val request = requests.single()
        assertEquals("/rest/v1/rpc/delete_my_account", request.url.encodedPath)
        assertEquals("POST", request.method.value)
        assertEquals(JsonObject(emptyMap()), Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
        assertEquals("Bearer ${token(alice)}", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `nothing is sent for an account the session does not belong to`() = runTest {
        assertFailure(RemoteFailure.ACCOUNT_MISMATCH) { remote.deleteAccount(bob) }
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `refusals and lost answers are told apart`() = runTest {
        answer = { respondError("42501", HttpStatusCode.Forbidden) }
        assertFailure(RemoteFailure.FORBIDDEN) { remote.deleteAccount(alice) }

        answer = { respond("""{"message":"JWT expired"}""", HttpStatusCode.Unauthorized, JSON_HEADERS) }
        assertFailure(RemoteFailure.UNAUTHENTICATED) { remote.deleteAccount(alice) }

        answer = { respondError("XX000", HttpStatusCode.InternalServerError) }
        assertFailure(RemoteFailure.SERVER) { remote.deleteAccount(alice) }

        answer = { throw IOException("offline") }
        assertFailure(RemoteFailure.NETWORK) { remote.deleteAccount(alice) }
    }

    private suspend fun assertFailure(expected: RemoteFailure, block: suspend () -> Unit) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is RemoteException)
        assertEquals(expected, (failure as RemoteException).failure)
    }

    private fun MockRequestHandleScope.respondError(code: String, status: HttpStatusCode) =
        respond("""{"code":"$code","message":"refused","details":null,"hint":null}""", status, JSON_HEADERS)

    private companion object {
        const val URL = "https://test.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_test-key"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
