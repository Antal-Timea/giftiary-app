package app.giftiary.data.auth

import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.AuthException
import app.giftiary.domain.repository.SignUpOutcome
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.Identity
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.util.UUID

/**
 * The status sequences below are the ones supabase-kt 3.8.0 produces; the stored user stands in
 * for its session storage, which it deletes before reporting NotAuthenticated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionStateTest {

    private val userId = UUID.randomUUID()
    private val user = UserInfo(aud = "authenticated", id = userId.toString(), email = "anna@example.com")
    private val signedIn = AuthState.SignedIn(AuthUser(userId, "anna@example.com"))
    private val offline = RefreshFailureCause.NetworkError(IOException("offline"))

    private val status = MutableStateFlow<SessionStatus>(SessionStatus.Initializing)
    private var storedUser: UserInfo? = null

    private fun session(user: UserInfo?) =
        UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer", user = user)

    private fun authenticated(source: SessionSource = SessionSource.Storage) =
        SessionStatus.Authenticated(session(user), source)

    private fun TestScope.collectStates(): List<AuthState> {
        val states = mutableListOf<AuthState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            status.toAuthStates { storedUser }.toList(states)
        }
        return states
    }

    @Test
    fun `a first launch without a saved session restores, then shows the auth flow`() = runTest {
        val states = collectStates()

        status.value = SessionStatus.NotAuthenticated()

        assertEquals(listOf(AuthState.Restoring, AuthState.SignedOut), states)
    }

    @Test
    fun `a saved session is signed in straight away, before the SDK has finished loading it`() = runTest {
        storedUser = user
        val states = collectStates()

        status.value = authenticated()

        assertEquals(listOf(signedIn), states)
    }

    @Test
    fun `leaving for the background and coming back never passes through restoring`() = runTest {
        storedUser = user
        status.value = authenticated(SessionSource.SignIn(Email))
        val states = collectStates()

        status.value = SessionStatus.Initializing // the SDK pauses auto-refresh when Giftiary stops
        status.value = authenticated() // and reloads the stored session when it starts again

        assertEquals(listOf(signedIn), states)
    }

    @Test
    fun `offline launch with a session the SDK keeps retrying to refresh is signed in`() = runTest {
        // A session close to expiry stays in Initializing while every refresh attempt fails.
        storedUser = user

        assertEquals(listOf(signedIn), collectStates())
    }

    @Test
    fun `offline launch with an expired session is still signed in`() = runTest {
        storedUser = user
        val states = collectStates()

        status.value = SessionStatus.RefreshFailure(offline)

        assertEquals(listOf(signedIn), states)
    }

    @Test
    fun `getting back online after a failed refresh keeps the same signed-in state`() = runTest {
        storedUser = user
        val states = collectStates()

        status.value = SessionStatus.RefreshFailure(offline)
        status.value = authenticated(SessionSource.Refresh(session(user)))

        assertEquals(listOf(signedIn), states)
    }

    @Test
    fun `a session the backend rejects is signed out once the SDK clears it`() = runTest {
        storedUser = user
        val states = collectStates()

        storedUser = null // clearSession() deletes the stored session...
        status.value = SessionStatus.NotAuthenticated(isSignOut = true) // ...then reports it

        assertEquals(listOf(signedIn, AuthState.SignedOut), states)
    }

    @Test
    fun `signing out leaves the app`() = runTest {
        storedUser = user
        status.value = authenticated()
        val states = collectStates()

        storedUser = null
        status.value = SessionStatus.NotAuthenticated(isSignOut = true)

        assertEquals(listOf(signedIn, AuthState.SignedOut), states)
    }

    @Test
    fun `a sign-up that started a session is signed in`() {
        assertEquals(SignUpOutcome.SIGNED_IN, signUpOutcome(sessionStarted = true, returnedUser = user.withEmailIdentity()))
    }

    @Test
    fun `a sign-up without a session waits for email confirmation`() {
        assertEquals(
            SignUpOutcome.CONFIRMATION_REQUIRED,
            signUpOutcome(sessionStarted = false, returnedUser = user.withEmailIdentity()),
        )
    }

    @Test
    fun `a placeholder user without identities means the address is already registered`() {
        val error = assertThrows(AuthException::class.java) {
            signUpOutcome(sessionStarted = false, returnedUser = user.copy(identities = emptyList()))
        }

        assertEquals(AuthError.EMAIL_ALREADY_REGISTERED, error.error)
    }

    private fun UserInfo.withEmailIdentity() = copy(
        identities = listOf(
            Identity(id = id, identityData = JsonObject(emptyMap()), provider = "email", userId = id),
        ),
    )
}
