package app.gifty.presentation.auth

import app.gifty.domain.model.AuthState
import app.gifty.domain.repository.AuthError
import app.gifty.domain.repository.AuthRepository
import app.gifty.domain.usecase.CredentialsInputError
import app.gifty.domain.usecase.SignInUseCase
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

class SignInViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository().apply { state.value = AuthState.SignedOut }
    private val viewModel = SignInViewModel(SignInUseCase(repository))

    private fun enter(email: String, password: String) {
        viewModel.onEmailChange(email)
        viewModel.onPasswordChange(password)
    }

    @Test
    fun `submitting is shown while signing in and a second tap is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate
        enter("anna@example.com", "secret-password")

        viewModel.submit()
        assertTrue(viewModel.uiState.value.isSubmitting)
        viewModel.submit()
        gate.complete(Unit)

        assertEquals(1, repository.signInCalls.size)
        assertFalse(viewModel.uiState.value.isSubmitting)
        assertTrue(repository.state.value is AuthState.SignedIn)
    }

    @Test
    fun `invalid input shows field errors that clear when the field changes`() = runTest {
        enter("anna", "")

        viewModel.submit()
        assertEquals(CredentialsInputError.EMAIL_INVALID, viewModel.uiState.value.emailError)
        assertEquals(CredentialsInputError.PASSWORD_REQUIRED, viewModel.uiState.value.passwordError)
        assertFalse(viewModel.uiState.value.isSubmitting)

        viewModel.onEmailChange("anna@example.com")
        assertNull(viewModel.uiState.value.emailError)
        assertEquals(CredentialsInputError.PASSWORD_REQUIRED, viewModel.uiState.value.passwordError)
    }

    @Test
    fun `a rejected sign-in shows the auth error and allows another attempt`() = runTest {
        repository.failure = AuthError.INVALID_CREDENTIALS
        enter("anna@example.com", "wrong-password")

        viewModel.submit()
        assertEquals(AuthError.INVALID_CREDENTIALS, viewModel.uiState.value.authError)
        assertFalse(viewModel.uiState.value.isSubmitting)

        repository.failure = null
        viewModel.onPasswordChange("right-password")
        assertNull(viewModel.uiState.value.authError)
        viewModel.submit()
        assertTrue(repository.state.value is AuthState.SignedIn)
    }

    @Test
    fun `an unexpected failure is shown as an unknown error`() = runTest {
        val viewModel = SignInViewModel(
            SignInUseCase(object : AuthRepository by repository {
                override suspend fun signIn(email: String, password: String) = throw IllegalStateException()
            }),
        )
        viewModel.onEmailChange("anna@example.com")
        viewModel.onPasswordChange("secret-password")

        viewModel.submit()

        assertEquals(AuthError.UNKNOWN, viewModel.uiState.value.authError)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}
