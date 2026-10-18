package app.giftiary.data.remote

import app.giftiary.data.remote.RemoteTestData.alice
import app.giftiary.data.remote.RemoteTestData.token
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.testing.FakeActiveOwner
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

/** The own profile against a fake PostgREST: exactly which row is read and written, and failures. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseProfileRepositoryTest {

    private val requests = mutableListOf<HttpRequestData>()
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondJson("[]") }
    private val engine = MockEngine { request ->
        requests += request
        answer(request)
    }
    private val auth: Auth = createSupabaseClient(URL, KEY) {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private fun repository(owner: UUID? = alice) =
        SupabaseProfileRepository(AccountBoundClients(URL, KEY, engine), RemoteAccounts(auth), FakeActiveOwner(owner))

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
    fun `the own row's name is read, as the account`() = runTest {
        answer = { respondJson("""[{"display_name":"Anna"}]""") }

        assertEquals("Anna", repository().displayName())

        val request = requests.single()
        assertEquals("/rest/v1/profiles", request.url.encodedPath)
        assertEquals("GET", request.method.value)
        assertEquals("eq.$alice", request.url.parameters["id"])
        assertEquals("display_name", request.url.parameters["select"])
        assertEquals("Bearer ${token(alice)}", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `no name is read as none, never as anything else`() = runTest {
        answer = { respondJson("""[{"display_name":null,"email":"x@example.com"}]""") }

        assertNull(repository().displayName())
    }

    @Test
    fun `a name is written to the own row only, and cleared as null`() = runTest {
        answer = { respondJson("""[{"display_name":"Anna"}]""") }
        repository().setDisplayName("Anna")
        answer = { respondJson("""[{"display_name":null}]""") }
        repository().setDisplayName(null)

        requests.forEach { request ->
            assertEquals("PATCH", request.method.value)
            assertEquals("eq.$alice", request.url.parameters["id"])
        }
        assertEquals(JsonPrimitive("Anna"), body(requests[0])["display_name"])
        assertEquals(JsonNull, body(requests[1])["display_name"])
        assertEquals(setOf("display_name"), body(requests[0]).keys)
    }

    @Test
    fun `no profile row is unavailable, more than one is refused`() = runTest {
        answer = { respondJson("[]") }
        assertFailure(ProfileFailure.UNAVAILABLE) { repository().displayName() }
        assertFailure(ProfileFailure.UNAVAILABLE) { repository().setDisplayName("Anna") }

        answer = { respondJson("""[{"display_name":"A"},{"display_name":"B"}]""") }
        assertFailure(ProfileFailure.SERVER) { repository().displayName() }
    }

    @Test
    fun `failures are typed and carry nothing of the answer`() = runTest {
        answer = { throw IOException("offline") }
        assertFailure(ProfileFailure.NETWORK) { repository().displayName() }

        answer = { respond("""{"code":"23514","message":"secret detail"}""", HttpStatusCode.BadRequest, JSON) }
        val failure = assertFailure(ProfileFailure.SERVER) { repository().setDisplayName("Anna") }
        assertNull(failure.cause)
        assertTrue(!failure.stackTraceToString().contains("secret detail"))
    }

    @Test
    fun `without an open account nothing is sent`() = runTest {
        assertFailure(ProfileFailure.SERVER) { repository(owner = null).displayName() }
        assertTrue(requests.isEmpty())
    }

    private fun body(request: HttpRequestData) =
        Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()).jsonObject

    private suspend fun assertFailure(expected: ProfileFailure, block: suspend () -> Unit): ProfileException {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("expected $expected but got $failure", failure is ProfileException)
        assertEquals(expected, (failure as ProfileException).failure)
        return failure
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(body, HttpStatusCode.OK, JSON)

    private companion object {
        const val URL = "https://test.supabase.co"
        const val KEY = "sb_publishable_test-key"
        val JSON = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
