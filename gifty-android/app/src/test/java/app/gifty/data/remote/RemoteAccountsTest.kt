package app.gifty.data.remote

import app.gifty.data.remote.RemoteTestData.alice
import app.gifty.data.remote.RemoteTestData.bob
import app.gifty.data.remote.RemoteTestData.token
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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Token changes, checked against the real supabase-kt [Auth] (in-memory storage, no network): the
 * coordinator's retry after a refresh relies on the SDK reporting a refreshed session this way.
 */
@OptIn(ExperimentalCoroutinesApi::class, SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class RemoteAccountsTest {

    private var refreshRequests = 0

    // Answers the SDK's refresh-token grant the way the Auth server does.
    private val engine = MockEngine { request ->
        check(request.url.encodedPath.endsWith("/auth/v1/token") && request.url.parameters["grant_type"] == "refresh_token")
        refreshRequests++
        respond(
            """{"access_token":"${token(alice, "refreshed")}","refresh_token":"r2","expires_in":3600,"token_type":"bearer",""" +
                """"user":{"id":"$alice","aud":"authenticated"}}""",
            HttpStatusCode.OK,
            headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        httpEngine = engine
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private val accounts = RemoteAccounts(auth)

    private fun session(owner: UUID, secret: String) = UserSession(
        accessToken = token(owner, secret),
        refreshToken = "refresh-$secret",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = owner.toString()),
    )

    private fun TestScope.changes(): List<Unit> {
        val changes = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { accounts.accessTokenChanges.toList(changes) }
        return changes
    }

    @Test
    fun `the SDK's own refresh reports the new token`() = runTest {
        auth.importSession(session(alice, "t1"), autoRefresh = false, source = SessionSource.Storage)
        val changes = changes()

        auth.refreshCurrentSession()

        assertEquals("one refresh request", 1, refreshRequests)
        val status = auth.sessionStatus.value as SessionStatus.Authenticated
        assertEquals(token(alice, "refreshed"), status.session.accessToken)
        assertEquals(2, changes.size)
        assertEquals(token(alice, "refreshed"), accounts.capture(alice).accessToken)
    }

    @Test
    fun `a new token, from a refresh or another sign-in, is reported each time`() = runTest {
        val first = session(alice, "t1")
        auth.importSession(first, autoRefresh = false, source = SessionSource.Storage)
        val changes = changes()
        assertEquals("the current token on collection", 1, changes.size)

        val refreshed = session(alice, "t2")
        auth.importSession(refreshed, autoRefresh = false, source = SessionSource.Refresh(first))
        assertEquals(2, changes.size)

        auth.clearSession()
        assertEquals("losing the session is not a change to retry on", 2, changes.size)

        auth.importSession(session(bob, "t3"), autoRefresh = false, source = SessionSource.Storage)
        assertEquals(3, changes.size)
    }

    @Test
    fun `the same token again, e g reloaded when the app returns, is not a change`() = runTest {
        val first = session(alice, "t1")
        auth.importSession(first, autoRefresh = false, source = SessionSource.Storage)
        val changes = changes()

        // What the SDK's lifecycle hook does across background and foreground.
        auth.stopAutoRefreshForCurrentSession()
        auth.setSessionStatus(SessionStatus.Initializing)
        auth.loadFromStorage(autoRefresh = false)
        auth.importSession(first, autoRefresh = false, source = SessionSource.Storage)

        assertEquals(1, changes.size)
    }

    @Test
    fun `without a session nothing is reported`() = runTest {
        val changes = changes()

        auth.clearSession()

        assertEquals(0, changes.size)
    }
}
