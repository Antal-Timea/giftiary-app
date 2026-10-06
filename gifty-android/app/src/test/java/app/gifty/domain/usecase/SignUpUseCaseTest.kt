package app.gifty.domain.usecase

import app.gifty.domain.model.AuthState
import app.gifty.domain.repository.AuthError
import app.gifty.domain.repository.SignUpOutcome
import app.gifty.testing.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpUseCaseTest {

    private val repository = FakeAuthRepository().apply { state.value = AuthState.SignedOut }
    private val signUp = SignUpUseCase(repository)

    @Test
    fun `a password shorter than the minimum is rejected before contacting the backend`() = runTest {
        val result = signUp("anna@example.com", "a".repeat(MIN_PASSWORD_LENGTH - 1), "a".repeat(MIN_PASSWORD_LENGTH - 1))

        assertEquals(SignUpResult.Invalid(setOf(CredentialsInputError.PASSWORD_TOO_SHORT)), result)
        assertTrue(repository.signUpCalls.isEmpty())
    }

    @Test
    fun `a password of exactly the minimum length is accepted`() = runTest {
        assertEquals(
            SignUpResult.ConfirmationRequired("anna@example.com"),
            signUp("anna@example.com", "a".repeat(MIN_PASSWORD_LENGTH), "a".repeat(MIN_PASSWORD_LENGTH)),
        )
    }

    @Test
    fun `email and password problems are reported together`() = runTest {
        assertEquals(
            SignUpResult.Invalid(
                setOf(
                    CredentialsInputError.EMAIL_INVALID,
                    CredentialsInputError.PASSWORD_REQUIRED,
                    CredentialsInputError.CONFIRMATION_REQUIRED,
                ),
            ),
            signUp("anna example.com", "", ""),
        )
    }

    @Test
    fun `a confirmation that differs from the password is rejected without contacting the backend`() = runTest {
        val result = signUp("anna@example.com", "long enough", "long enuogh")

        assertEquals(SignUpResult.Invalid(setOf(CredentialsInputError.PASSWORDS_DO_NOT_MATCH)), result)
        assertTrue(repository.signUpCalls.isEmpty())
    }

    @Test
    fun `a missing confirmation is required`() = runTest {
        assertEquals(
            SignUpResult.Invalid(setOf(CredentialsInputError.CONFIRMATION_REQUIRED)),
            signUp("anna@example.com", "long enough", ""),
        )
    }

    @Test
    fun `a matching confirmation does not excuse a short password`() = runTest {
        assertEquals(
            SignUpResult.Invalid(setOf(CredentialsInputError.PASSWORD_TOO_SHORT)),
            signUp("anna@example.com", "short", "short"),
        )
    }

    @Test
    fun `only the password itself is sent, never the confirmation`() = runTest {
        signUp("anna@example.com", "long enough", "long enough")

        assertEquals(listOf("anna@example.com" to "long enough"), repository.signUpCalls)
    }

    @Test
    fun `a sign-up awaiting email confirmation does not sign anyone in`() = runTest {
        val result = signUp(" anna@example.com ", "long enough", "long enough")

        assertEquals(SignUpResult.ConfirmationRequired("anna@example.com"), result)
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun `a backend that confirms immediately reports a signed-in result`() = runTest {
        repository.signUpOutcome = SignUpOutcome.SIGNED_IN

        assertEquals(SignUpResult.SignedIn, signUp("anna@example.com", "long enough", "long enough"))
    }

    @Test
    fun `an already registered address is reported as such`() = runTest {
        repository.failure = AuthError.EMAIL_ALREADY_REGISTERED

        assertEquals(SignUpResult.Failed(AuthError.EMAIL_ALREADY_REGISTERED), signUp("anna@example.com", "long enough", "long enough"))
    }
}
