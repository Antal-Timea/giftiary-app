package app.gifty.data.auth

import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import app.gifty.domain.repository.AuthError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.util.UUID

class AuthMappingTest {

    private val userId = UUID.randomUUID()
    private val user = UserInfo(aud = "authenticated", id = userId.toString(), email = "anna@example.com")
    private val signedIn = AuthState.SignedIn(AuthUser(userId, "anna@example.com"))

    private fun session(user: UserInfo?) =
        UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer", user = user)

    @Test
    fun `restoring the saved session is neither signed in nor signed out`() {
        assertEquals(AuthState.Restoring, SessionStatus.Initializing.toAuthState(currentUser = null))
    }

    @Test
    fun `an authenticated session maps to its user`() {
        assertEquals(signedIn, SessionStatus.Authenticated(session(user)).toAuthState(currentUser = null))
    }

    @Test
    fun `a session without embedded user falls back to the current user`() {
        assertEquals(signedIn, SessionStatus.Authenticated(session(user = null)).toAuthState(currentUser = user))
    }

    @Test
    fun `no session is signed out`() {
        assertEquals(AuthState.SignedOut, SessionStatus.NotAuthenticated(isSignOut = true).toAuthState(currentUser = null))
    }

    @Test
    fun `a failed refresh, e g offline, keeps the user signed in`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        assertEquals(signedIn, status.toAuthState(currentUser = user))
    }

    @Test
    fun `a failed refresh without any session is signed out`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))

        assertEquals(AuthState.SignedOut, status.toAuthState(currentUser = null))
    }

    @Test
    fun `backend error codes map to app errors`() {
        val expected = mapOf(
            AuthErrorCode.InvalidCredentials to AuthError.INVALID_CREDENTIALS,
            AuthErrorCode.EmailExists to AuthError.EMAIL_ALREADY_REGISTERED,
            AuthErrorCode.UserAlreadyExists to AuthError.EMAIL_ALREADY_REGISTERED,
            AuthErrorCode.EmailNotConfirmed to AuthError.EMAIL_NOT_CONFIRMED,
            AuthErrorCode.WeakPassword to AuthError.WEAK_PASSWORD,
            AuthErrorCode.EmailAddressInvalid to AuthError.INVALID_EMAIL,
            AuthErrorCode.OverRequestRateLimit to AuthError.RATE_LIMITED,
            AuthErrorCode.OverEmailSendRateLimit to AuthError.RATE_LIMITED,
            AuthErrorCode.UnexpectedFailure to AuthError.UNKNOWN,
        )

        expected.forEach { (code, error) -> assertEquals(code.name, error, authErrorFor(code, statusCode = 400)) }
    }

    @Test
    fun `an unrecognised error with status 429 is rate limiting`() {
        assertEquals(AuthError.RATE_LIMITED, authErrorFor(code = null, statusCode = 429))
        assertEquals(AuthError.UNKNOWN, authErrorFor(code = null, statusCode = 500))
    }

    @Test
    fun `connection problems are network errors`() {
        assertEquals(AuthError.NETWORK_UNAVAILABLE, IOException("no route").toAuthError())
        assertEquals(AuthError.NETWORK_UNAVAILABLE, HttpRequestTimeoutException("https://example.test", 1000).toAuthError())
        assertEquals(AuthError.UNKNOWN, IllegalStateException().toAuthError())
    }
}
