package app.gifty.domain.usecase

import app.gifty.domain.repository.AuthError
import app.gifty.testing.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInUseCaseTest {

    private val repository = FakeAuthRepository()
    private val signIn = SignInUseCase(repository)

    @Test
    fun `missing email and password are reported without contacting the backend`() = runTest {
        val result = signIn("  ", "")

        assertEquals(
            SignInResult.Invalid(setOf(CredentialsInputError.EMAIL_REQUIRED, CredentialsInputError.PASSWORD_REQUIRED)),
            result,
        )
        assertTrue(repository.signInCalls.isEmpty())
    }

    @Test
    fun `an address without a domain is rejected`() = runTest {
        assertEquals(SignInResult.Invalid(setOf(CredentialsInputError.EMAIL_INVALID)), signIn("anna@", "secret"))
    }

    @Test
    fun `existing passwords are not held to the sign-up length rule`() = runTest {
        assertEquals(SignInResult.SignedIn, signIn("anna@example.com", "short"))
    }

    @Test
    fun `the email is trimmed but the password is sent exactly as typed`() = runTest {
        signIn("  anna@example.com ", " pass word ")

        assertEquals(listOf("anna@example.com" to " pass word "), repository.signInCalls)
    }

    @Test
    fun `a backend failure is returned as its auth error`() = runTest {
        repository.failure = AuthError.INVALID_CREDENTIALS

        assertEquals(SignInResult.Failed(AuthError.INVALID_CREDENTIALS), signIn("anna@example.com", "wrong-password"))
    }
}
