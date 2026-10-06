package app.gifty.presentation.auth

import app.gifty.domain.model.AuthState
import app.gifty.domain.repository.AuthError
import app.gifty.domain.repository.SignUpOutcome
import app.gifty.domain.usecase.CredentialsInputError
import app.gifty.domain.usecase.SignUpUseCase
import app.gifty.testing.FakeAuthRepository
import app.gifty.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CreateAccountViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository().apply { state.value = AuthState.SignedOut }
    private val viewModel = CreateAccountViewModel(SignUpUseCase(repository))

    private fun enter(email: String, password: String, confirmation: String = password) {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
        viewModel.onPasswordConfirmationChange(confirmation)
    }

    @Test
    fun `a successful sign-up asks to check the email and stays signed out`() = runTest {
        enter("anna@example.com", "long enough")

        viewModel.submit()

        assertEquals("anna@example.com", viewModel.uiState.value.confirmationSentTo)
        assertFalse(viewModel.uiState.value.credentials.isSubmitting)
        assertEquals(AuthState.SignedOut, repository.state.value)
    }

    @Test
    fun `submitting is shown while the account is created and a second tap is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        enter("anna@example.com", "long enough")

        viewModel.submit()
        assertTrue(viewModel.uiState.value.credentials.isSubmitting)
        viewModel.submit()
        gate.complete(Unit)

        assertEquals(1, repository.signUpCalls.size)
    }

    @Test
    fun `once the confirmation email is sent, submitting again does not create another sign-up`() = runTest {
        enter("anna@example.com", "long enough")
        viewModel.submit()

        viewModel.submit()

        assertEquals(1, repository.signUpCalls.size)
    }

    @Test
    fun `a short password is reported on the password field`() = runTest {
        enter("anna@example.com", "short")

        viewModel.submit()

        assertEquals(CredentialsInputError.PASSWORD_TOO_SHORT, viewModel.uiState.value.credentials.passwordError)
        assertNull(viewModel.uiState.value.confirmationSentTo)
        assertTrue(repository.signUpCalls.isEmpty())
    }

    @Test
    fun `mismatched passwords show an error on the confirmation field and never reach the backend`() = runTest {
        enter("anna@example.com", "long enough", confirmation = "long enuogh")

        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(CredentialsInputError.PASSWORDS_DO_NOT_MATCH, state.confirmationError)
        assertNull(state.credentials.passwordError)
        assertFalse(state.credentials.isSubmitting)
        assertNull(state.confirmationSentTo)
        assertTrue(repository.signUpCalls.isEmpty())
    }

    @Test
    fun `fixing either password clears the mismatch, and a matching pair signs up`() = runTest {
        enter("anna@example.com", "long enough", confirmation = "long enuogh")
        viewModel.submit()

        viewModel.onPasswordConfirmationChange("long enough")
        assertNull(viewModel.uiState.value.confirmationError)
        viewModel.submit()

        assertEquals(1, repository.signUpCalls.size)
        assertEquals("anna@example.com", viewModel.uiState.value.confirmationSentTo)
    }

    @Test
    fun `changing the password also clears a mismatch shown on the confirmation`() = runTest {
        enter("anna@example.com", "long enough", confirmation = "")
        viewModel.submit()
        assertEquals(CredentialsInputError.CONFIRMATION_REQUIRED, viewModel.uiState.value.confirmationError)

        viewModel.onPasswordChange("another password")

        assertNull(viewModel.uiState.value.confirmationError)
    }

    @Test
    fun `a backend failure is shown without moving on`() = runTest {
        repository.failure = AuthError.EMAIL_ALREADY_REGISTERED
        enter("anna@example.com", "long enough")

        viewModel.submit()

        assertEquals(AuthError.EMAIL_ALREADY_REGISTERED, viewModel.uiState.value.credentials.authError)
        assertFalse(viewModel.uiState.value.credentials.isSubmitting)
        assertNull(viewModel.uiState.value.confirmationSentTo)
    }

    @Test
    fun `an immediately confirmed account leaves the switch to the auth gate`() = runTest {
        repository.signUpOutcome = SignUpOutcome.SIGNED_IN
        enter("anna@example.com", "long enough")

        viewModel.submit()

        assertNull(viewModel.uiState.value.confirmationSentTo)
        assertTrue(repository.state.value is AuthState.SignedIn)
    }
}
