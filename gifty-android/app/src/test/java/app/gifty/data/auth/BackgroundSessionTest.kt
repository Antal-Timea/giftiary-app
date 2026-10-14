package app.gifty.data.auth

import app.gifty.domain.model.AuthState
import app.gifty.testing.MutableClock
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.logging.LogLevel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.time.Instant
import java.util.Collections
import java.util.UUID
import kotlin.time.toKotlinInstant

/**
 * Background session restore against the pinned supabase-kt Auth itself, with only its HTTP engine
 * replaced. The SDK is put into the state its own Android lifecycle hook leaves behind when the app
 * goes to the background (refresh stopped, status Initializing, session still stored), then
 * [BackgroundSession] must make it usable, through the SDK's public API, and never leave the SDK's
 * automatic refresh running.
 */
@OptIn(ExperimentalCoroutinesApi::class, SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class BackgroundSessionTest {

    private val now = Instant.parse("2026-10-14T12:00:00Z")
    private val clock = MutableClock(now)
    private val userId = UUID.randomUUID()
    private val user = UserInfo(aud = "authenticated", id = userId.toString(), email = "anna@example.com")
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())
    private val refreshed = session("refreshed-access", "refreshed-refresh", expiresAt = now.plusSeconds(3600))
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respondSession(refreshed) }

    private val engine = MockEngine { request ->
        requests += request
        answer(request)
    }
    private val sessionManager = MemorySessionManager()
    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        defaultLogLevel = LogLevel.NONE
        httpEngine = engine
        install(Auth) {
            sessionManager = this@BackgroundSessionTest.sessionManager
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            // As the app has it: the SDK's own refresh is on, so a leak of it would show.
            alwaysAutoRefresh = true
        }
    }.auth
    private val background = BackgroundSession(auth, clock)

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun `in the background the stored session gives no token until it is restored`() = runTest {
        sentToBackground(session("access", "refresh", expiresAt = now.plusSeconds(3000)))

        assertNull(auth.currentSessionOrNull())
        assertEquals(BackgroundSessionResult.READY, background.prepare())

        assertEquals("access", auth.currentSessionOrNull()?.accessToken)
        assertTrue("a valid session needs no request", requests.isEmpty())
        assertFalse(auth.isAutoRefreshRunning)
    }

    @Test
    fun `an expired session is refreshed once, kept for later, and the SDK's refresh is not started`() = runTest {
        sentToBackground(session("expired-access", "old-refresh", expiresAt = now.minusSeconds(60)))

        assertEquals(BackgroundSessionResult.READY, background.prepare())

        val request = requests.single()
        assertTrue(request.url.toString(), request.url.encodedPath.endsWith("/auth/v1/token"))
        assertEquals("refresh_token", request.url.parameters["grant_type"])
        assertTrue(String(request.body.toByteArray()).contains("\"old-refresh\""))
        assertEquals("refreshed-access", auth.currentSessionOrNull()?.accessToken)
        assertEquals("refreshed-refresh", sessionManager.loadSession()?.refreshToken)
        assertFalse(auth.isAutoRefreshRunning)
    }

    @Test
    fun `a session about to expire is refreshed too`() = runTest {
        sentToBackground(session("access", "refresh", expiresAt = now.plusSeconds(120)))

        assertEquals(BackgroundSessionResult.READY, background.prepare())

        assertEquals(1, requests.size)
        assertEquals("refreshed-access", auth.currentSessionOrNull()?.accessToken)
    }

    @Test
    fun `no network or a failing server is worth another try later, and nothing keeps retrying`() = runTest {
        listOf<suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData>(
            { throw IOException("offline") },
            { respondError(HttpStatusCode.ServiceUnavailable, "server_error") },
        ).forEach { failing ->
            requests.clear()
            sentToBackground(session("expired-access", "refresh", expiresAt = now.minusSeconds(60)))
            answer = failing

            assertEquals(BackgroundSessionResult.RETRY_LATER, background.prepare())

            assertEquals(1, requests.size)
            assertFalse("the SDK's retrying refresh must not be left running", auth.isAutoRefreshRunning)
        }
    }

    @Test
    fun `a refresh token the backend rejects is not retried, and the session is left to the SDK`() = runTest {
        sentToBackground(session("expired-access", "revoked", expiresAt = now.minusSeconds(60)))
        answer = { respondError(HttpStatusCode.BadRequest, "refresh_token_not_found") }

        assertEquals(BackgroundSessionResult.REJECTED, background.prepare())

        assertEquals(1, requests.size)
        assertFalse(auth.isAutoRefreshRunning)
        assertEquals("revoked", sessionManager.loadSession()?.refreshToken)
    }

    @Test
    fun `without a stored session nobody is signed in`() = runTest {
        assertEquals(BackgroundSessionResult.NO_SESSION, background.prepare())

        auth.setSessionStatus(SessionStatus.NotAuthenticated(isSignOut = true))
        assertEquals(BackgroundSessionResult.NO_SESSION, background.prepare())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an SDK refresh already under way is never raced`() = runTest {
        val expired = session("expired-access", "refresh", expiresAt = now.minusSeconds(60))
        sessionManager.saveSession(expired)
        val held = CompletableDeferred<Unit>()
        val arrived = CompletableDeferred<Unit>()
        answer = {
            arrived.complete(Unit)
            held.await()
            respondSession(refreshed)
        }
        // As on a cold start: the SDK loads the stored session and refreshes it on its own.
        val load = launch { auth.loadFromStorage() }
        arrived.await()

        assertEquals(BackgroundSessionResult.RETRY_LATER, background.prepare())

        assertEquals("only the SDK's own request", 1, requests.size)
        held.complete(Unit)
        load.join()
    }

    @Test
    fun `restoring reports the same account again, so the app stays signed in as that account only`() = runTest {
        val stored = session("access", "refresh", expiresAt = now.plusSeconds(3000))
        auth.importSession(stored, autoRefresh = false, source = SessionSource.Storage)
        val states = mutableListOf<AuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SupabaseAuthRepository(auth).authState.toList(states)
        }
        auth.stopAutoRefreshForCurrentSession()
        auth.setSessionStatus(SessionStatus.Initializing)

        assertEquals(BackgroundSessionResult.READY, background.prepare())

        assertEquals(1, states.size)
        assertEquals(userId, (states.single() as AuthState.SignedIn).user.id)
    }

    /** What the SDK's lifecycle hook leaves when the app goes to the background. */
    private suspend fun sentToBackground(stored: UserSession) {
        auth.importSession(stored, autoRefresh = false, source = SessionSource.Storage)
        auth.stopAutoRefreshForCurrentSession()
        auth.setSessionStatus(SessionStatus.Initializing)
    }

    private fun session(access: String, refresh: String, expiresAt: Instant) = UserSession(
        accessToken = access,
        refreshToken = refresh,
        expiresIn = 3600,
        tokenType = "bearer",
        user = user,
        expiresAt = expiresAt.toKotlinInstant(),
    )

    private fun MockRequestHandleScope.respondSession(session: UserSession) = respond(
        Json.encodeToString(UserSession.serializer(), session),
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun MockRequestHandleScope.respondError(status: HttpStatusCode, code: String) = respond(
        """{"code":${status.value},"error_code":"$code","msg":"$code"}""",
        status,
        headersOf(HttpHeaders.ContentType, "application/json"),
    )
}
