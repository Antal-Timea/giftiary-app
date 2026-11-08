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
import java.util.UUID

/** The deletion RPC against a fake PostgREST: what is sent, and how refusals are read. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseAccountDeletionRemoteTest {

    private val requests = mutableListOf<HttpRequestData>()
    // Each bucket's folder lists as empty unless a test fills it; only the RPC's answer varies.
    private var folder = mutableListOf<String>()
    private var avatarFolder = mutableListOf<String>()
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) }
    private val engine = MockEngine { request ->
        requests += request
        val listed = if (request.url.encodedPath.endsWith("/profile-avatars")) avatarFolder else folder
        when {
            request.url.encodedPath.startsWith("/storage/v1/object/list/") ->
                respond(listed.joinToString(prefix = "[", postfix = "]", transform = ::listed), HttpStatusCode.OK, JSON_HEADERS)
            request.url.encodedPath.startsWith("/storage/v1/object/") -> {
                listed.clear()
                respond("[]", HttpStatusCode.OK, JSON_HEADERS)
            }
            else -> answer(request)
        }
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
    private val clients = AccountBoundClients(URL, PUBLISHABLE_KEY, engine)
    private val remote = SupabaseAccountDeletionRemote(
        clients,
        RemoteAccounts(auth),
        SupabaseGiftPhotoRemote(clients),
        SupabaseProfileAvatarRemote(clients),
    )

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

        val request = requests.last()
        assertEquals("/rest/v1/rpc/delete_my_account", request.url.encodedPath)
        assertEquals("POST", request.method.value)
        assertEquals(JsonObject(emptyMap()), Json.parseToJsonElement((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()))
        assertEquals("Bearer ${token(alice)}", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `the account's photo folder is emptied before the account is deleted`() = runTest {
        folder += listOf("10000000-0000-4000-8000-000000000001.jpg", "10000000-0000-4000-8000-000000000002.jpg")

        remote.deleteAccount(alice)

        val paths = requests.map { "${it.method.value} ${it.url.encodedPath}" }
        assertEquals(
            listOf(
                "POST /storage/v1/object/list/gift-photos",
                "DELETE /storage/v1/object/gift-photos",
                "POST /storage/v1/object/list/profile-avatars",
                "POST /rest/v1/rpc/delete_my_account",
            ),
            paths,
        )
        val deleted = Json.parseToJsonElement((requests[1].body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        assertEquals(
            Json.parseToJsonElement(
                """{"prefixes":["$alice/10000000-0000-4000-8000-000000000001.jpg","$alice/10000000-0000-4000-8000-000000000002.jpg"]}""",
            ),
            deleted,
        )
        assertEquals("Bearer ${token(alice)}", requests[0].headers[HttpHeaders.Authorization])
    }

    @Test
    fun `the account's avatars are emptied too, after its photos and before the account`() = runTest {
        avatarFolder += listOf("20000000-0000-4000-8000-000000000001.jpg")

        remote.deleteAccount(alice)

        assertEquals(
            listOf(
                "POST /storage/v1/object/list/gift-photos",
                "POST /storage/v1/object/list/profile-avatars",
                "DELETE /storage/v1/object/profile-avatars",
                "POST /rest/v1/rpc/delete_my_account",
            ),
            requests.map { "${it.method.value} ${it.url.encodedPath}" },
        )
        val deleted = Json.parseToJsonElement((requests[2].body as OutgoingContent.ByteArrayContent).bytes().decodeToString())
        assertEquals(Json.parseToJsonElement("""{"prefixes":["$alice/20000000-0000-4000-8000-000000000001.jpg"]}"""), deleted)
    }

    @Test
    fun `a failure to empty the avatars deletes no account`() = runTest {
        val avatarsDown = MockEngine { request ->
            requests += request
            when {
                request.url.encodedPath.endsWith("/profile-avatars") -> throw IOException("offline")
                request.url.encodedPath.startsWith("/storage/") -> respond("[]", HttpStatusCode.OK, JSON_HEADERS)
                else -> respond("", HttpStatusCode.NoContent)
            }
        }
        val down = AccountBoundClients(URL, PUBLISHABLE_KEY, avatarsDown)
        val deletion = SupabaseAccountDeletionRemote(down, RemoteAccounts(auth), SupabaseGiftPhotoRemote(down), SupabaseProfileAvatarRemote(down))

        assertFailure(RemoteFailure.NETWORK) { deletion.deleteAccount(alice) }
        assertTrue(requests.none { it.url.encodedPath.startsWith("/rest/") })
        avatarsDown.close()
    }

    @Test
    fun `a failure to empty the photo folder deletes nothing else`() = runTest {
        val storageDown = MockEngine { request ->
            requests += request
            if (request.url.encodedPath.startsWith("/storage/")) throw IOException("offline") else respond("", HttpStatusCode.NoContent)
        }
        val failing = AccountBoundClients(URL, PUBLISHABLE_KEY, storageDown)
        val deletion = SupabaseAccountDeletionRemote(failing, RemoteAccounts(auth), SupabaseGiftPhotoRemote(failing), SupabaseProfileAvatarRemote(failing))

        assertFailure(RemoteFailure.NETWORK) { deletion.deleteAccount(alice) }
        assertTrue(requests.none { it.url.encodedPath.startsWith("/rest/") })
        storageDown.close()
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

    /** An object as Storage lists it. */
    private fun listed(name: String) =
        """{"name":"$name","id":"${UUID.randomUUID()}","updated_at":null,"created_at":null,"last_accessed_at":null,"metadata":{}}"""

    private fun MockRequestHandleScope.respondError(code: String, status: HttpStatusCode) =
        respond("""{"code":"$code","message":"refused","details":null,"hint":null}""", status, JSON_HEADERS)

    private companion object {
        const val URL = "https://test.supabase.co"
        const val PUBLISHABLE_KEY = "sb_publishable_test-key"
        val JSON_HEADERS = headersOf(HttpHeaders.ContentType, "application/json")
    }
}
