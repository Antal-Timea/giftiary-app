package app.giftiary.presentation.auth

import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class AuthGateViewModelTest {

    private val alice = AuthUser(UUID.randomUUID(), "alice@example.com")
    private val bob = AuthUser(UUID.randomUUID(), "bob@example.com")

    private class FakeAccountSession : AccountSession {
        override val state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Restoring)
        var retries = 0
        var signOuts = 0
        override fun retry() {
            retries++
        }
        override fun signOut() {
            signOuts++
        }
    }

    private val session = FakeAccountSession()
    private val viewModel = AuthGateViewModel(session)

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
    fun `a failed preparation offers retry and sign-out`() {
        session.state.value = OwnerSessionState.Failed(alice)

        viewModel.retry()
        viewModel.signOut()

        assertEquals(OwnerSessionState.Failed(alice), viewModel.state.value)
        assertEquals(1, session.retries)
        assertEquals(1, session.signOuts)
    }
}
