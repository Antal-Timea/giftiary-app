package app.giftiary.presentation.auth

import app.giftiary.data.auth.PasswordRecovery
import app.giftiary.data.auth.RecoveryLink
import app.giftiary.data.auth.RecoveryState
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.testing.FakeAuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class AuthGateViewModelTest {

    private val alice = AuthUser(UUID.randomUUID(), "alice@example.com")
    private val bob = AuthUser(UUID.randomUUID(), "bob@example.com")

    private class FakeAccountSession : AccountSession {
        override val state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Restoring)
        var signOuts = 0
        override fun signOut() {
            signOuts++
        }
    }

    private val session = FakeAccountSession()
    private val auth = FakeAuthRepository().apply { state.value = AuthState.SignedOut }
    private val recovery = PasswordRecovery(auth, CoroutineScope(Dispatchers.Unconfined))
    private val viewModel = AuthGateViewModel(session, recovery)

    @Test
    fun `a password recovery under way is what the gate shows`() {
        assertEquals(RecoveryState.Idle, viewModel.recoveryState.value)

        recovery.start(RecoveryLink.Code("code"))
        assertEquals(RecoveryState.ChoosingPassword(), viewModel.recoveryState.value)

        recovery.setPassword("new-password-1")
        assertEquals(RecoveryState.Idle, viewModel.recoveryState.value)
        assertEquals(true, viewModel.passwordChanged.value)
        viewModel.onPasswordChangedShown()
        assertEquals(false, viewModel.passwordChanged.value)
    }

    @Test
    fun `the gate follows the session from startup to an open account`() {
        val seen = mutableListOf(viewModel.state.value)

        session.state.value = OwnerSessionState.SignedOut
        seen += viewModel.state.value
        session.state.value = OwnerSessionState.Restoring
        seen += viewModel.state.value
        session.state.value = OwnerSessionState.Ready(alice)
        seen += viewModel.state.value

        assertEquals(
            listOf(
                OwnerSessionState.Restoring,
                OwnerSessionState.SignedOut,
                OwnerSessionState.Restoring,
                OwnerSessionState.Ready(alice),
            ),
            seen,
        )
    }

    @Test
    fun `switching accounts passes through a closed state before the next account opens`() {
        session.state.value = OwnerSessionState.Ready(alice)
        val seen = mutableListOf(viewModel.state.value)

        session.state.value = OwnerSessionState.Restoring
        seen += viewModel.state.value
        session.state.value = OwnerSessionState.Ready(bob)
        seen += viewModel.state.value

        assertEquals(
            listOf(OwnerSessionState.Ready(alice), OwnerSessionState.Restoring, OwnerSessionState.Ready(bob)),
            seen,
        )
    }

    @Test
    fun `sign-out goes to the session`() {
        session.state.value = OwnerSessionState.Ready(alice)

        viewModel.signOut()

        assertEquals(1, session.signOuts)
    }
}
