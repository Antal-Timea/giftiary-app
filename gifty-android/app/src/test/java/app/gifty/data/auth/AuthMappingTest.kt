package app.gifty.data.auth

import app.gifty.domain.repository.AuthError
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.ktor.client.plugins.HttpRequestTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class AuthMappingTest {

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
