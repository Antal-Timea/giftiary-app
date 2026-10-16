package app.giftiary.data.auth

import app.giftiary.domain.model.AuthState
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

/**
 * Drives the real supabase-kt [Auth] (in-memory storage, no network) through the calls its own
 * Android lifecycle hook and refresh logic make, so the repository is checked against the SDK's
 * actual state handling rather than against assumptions about it.
 */
@OptIn(ExperimentalCoroutinesApi::class, SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
class SupabaseAuthRepositoryTest {

    private val userId = UUID.randomUUID()
    private val session = UserSession(
        accessToken = "access",
        refreshToken = "refresh",
        expiresIn = 3600,
        tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = userId.toString(), email = "anna@example.com"),
    )

    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth

    /** Signs in through the SDK, which also persists the session, then records every app state. */
    private suspend fun TestScope.signedInStates(): List<AuthState> {
        auth.importSession(session, autoRefresh = false, source = SessionSource.Storage)
        val states = mutableListOf<AuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            SupabaseAuthRepository(auth).authState.toList(states)
        }
        return states
    }

    private fun AuthState.userId() = (this as AuthState.SignedIn).user.id

    @Test
    fun `leaving for the background and returning keeps the app signed in without restoring`() = runTest {
        val states = signedInStates()

        // What the SDK's lifecycle hook does when Giftiary stops: pause refresh, report Initializing.
        auth.stopAutoRefreshForCurrentSession()
        auth.setSessionStatus(SessionStatus.Initializing)
        assertNotNull("the session must still be stored", auth.sessionManager.loadSession().user)
        // And when Giftiary starts again: reload the stored session.
        auth.loadFromStorage(autoRefresh = false)

        assertFalse(AuthState.Restoring in states)
        assertEquals(1, states.size)
        assertEquals(userId, states.single().userId())
    }

    @Test
    fun `a refresh that fails offline keeps the app signed in`() = runTest {
        val states = signedInStates()

        auth.setSessionStatus(SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline"))))

        assertEquals(1, states.size)
        assertEquals(userId, states.single().userId())
    }

    @Test
    fun `a session the SDK clears, e g after the backend rejected it, signs out`() = runTest {
        val states = signedInStates()

        auth.clearSession()

        assertEquals(2, states.size)
        assertEquals(userId, states.first().userId())
        assertEquals(AuthState.SignedOut, states.last())
    }
}
