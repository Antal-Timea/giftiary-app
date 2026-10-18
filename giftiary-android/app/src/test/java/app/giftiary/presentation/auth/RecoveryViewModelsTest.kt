package app.giftiary.presentation.auth

import app.giftiary.data.auth.PasswordRecovery
import app.giftiary.data.auth.RecoveryLink
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.AuthError
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.domain.usecase.CredentialsInputError
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecoveryViewModelsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val auth = FakeAuthRepository().apply { state.value = AuthState.SignedOut }
    private var signOuts = 0
    private val session = object : AccountSession {
        override val state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.SignedOut)
        override fun retry() = Unit

        // The ordinary sign-out: the session ends right after.
        override fun signOut() {
            signOuts++
            auth.state.value = AuthState.SignedOut
        }
    }

    private fun TestScope.recovery() =
        PasswordRecovery(auth, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    // ---- forgot password ----

    @Test
    fun `a reset is requested for the trimmed address and confirmed the same way for any address`() = runTest {
        val viewModel = ForgotPasswordViewModel(" anna@example.com ", auth)

        viewModel.onSubmit()

        assertEquals(listOf("anna@example.com"), auth.resetCalls)
        assertTrue(viewModel.state.value.sent)
        viewModel.onSubmit()
        assertEquals("sent once", 1, auth.resetCalls.size)
    }

    @Test
    fun `an address that is clearly wrong is refused before asking`() = runTest {
        val viewModel = ForgotPasswordViewModel("", auth)

        viewModel.onSubmit()
        assertEquals(CredentialsInputError.EMAIL_REQUIRED, viewModel.state.value.emailError)
        viewModel.onEmailChange("anna")
        viewModel.onSubmit()

        assertEquals(CredentialsInputError.EMAIL_INVALID, viewModel.state.value.emailError)
        assertTrue(auth.resetCalls.isEmpty())
    }

    @Test
    fun `a failed request says why and can be sent again`() = runTest {
        val viewModel = ForgotPasswordViewModel("anna@example.com", auth)
        auth.resetFailure = AuthError.RATE_LIMITED

        viewModel.onSubmit()
        assertEquals(AuthError.RATE_LIMITED, viewModel.state.value.authError)
        assertFalse(viewModel.state.value.sent)

        auth.resetFailure = null
        viewModel.onSubmit()
        assertTrue(viewModel.state.value.sent)
        assertNull(viewModel.state.value.authError)
    }

    // ---- new password ----

    @Test
    fun `a new password is checked here before it is sent`() = runTest {
        val recovery = recovery().apply { start(RecoveryLink.Code("code")) }
        val viewModel = RecoveryViewModel(recovery, session)

        viewModel.onPasswordChange("short")
        viewModel.onConfirmationChange("other")
        viewModel.onSubmit()

        assertEquals(CredentialsInputError.PASSWORD_TOO_SHORT, viewModel.input.value.passwordError)
        assertEquals(CredentialsInputError.PASSWORDS_DO_NOT_MATCH, viewModel.input.value.confirmationError)
        assertTrue(auth.setPasswordCalls.isEmpty())
    }

    @Test
    fun `a valid new password is handed over and then forgotten here`() = runTest {
        val recovery = recovery().apply { start(RecoveryLink.Code("code")) }
        val viewModel = RecoveryViewModel(recovery, session)

        viewModel.onPasswordChange("new-password-1")
        viewModel.onConfirmationChange("new-password-1")
        assertFalse("typed, it still never shows in text", viewModel.input.value.toString().contains("new-password-1"))
        viewModel.onSubmit()

        assertEquals(listOf("new-password-1"), auth.setPasswordCalls)
        assertEquals("", viewModel.input.value.password)
        assertEquals(RecoveryState.Idle, viewModel.state.value)
    }

    @Test
    fun `cancelling after the link was exchanged goes through the ordinary sign-out`() = runTest {
        val recovery = recovery().apply { start(RecoveryLink.Code("code")) }
        val viewModel = RecoveryViewModel(recovery, session)

        viewModel.onLeave()

        assertEquals(1, signOuts)
        assertEquals(RecoveryState.Idle, recovery.state.value)
        assertFalse(recovery.isActive.value)
    }

    @Test
    fun `signing out another account leaves the link to be opened again`() = runTest {
        auth.state.value = AuthState.SignedIn(auth.recoveryUser)
        val recovery = recovery().apply { start(RecoveryLink.Code("code")) }
        val viewModel = RecoveryViewModel(recovery, session)

        viewModel.onSignOutOtherAccount()

        assertEquals(1, signOuts)
        assertEquals(RecoveryState.Idle, recovery.state.value)
        assertTrue(auth.exchangeCalls.isEmpty())
    }
}
