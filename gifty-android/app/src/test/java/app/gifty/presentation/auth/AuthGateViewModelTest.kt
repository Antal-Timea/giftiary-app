package app.gifty.presentation.auth

import app.gifty.domain.model.AuthState
import app.gifty.domain.model.AuthUser
import app.gifty.domain.repository.AuthRepository
import app.gifty.testing.FakeAuthRepository
import app.gifty.testing.MainDispatcherRule
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class AuthGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeAuthRepository()
    private val anna = AuthState.SignedIn(AuthUser(UUID.randomUUID(), "anna@example.com"))

    @Test
    fun `nothing is shown as signed out before the repository has reported`() = runTest {
        val viewModel = AuthGateViewModel(object : AuthRepository by repository {
            override val authState = emptyFlow<AuthState>()
        })

        assertEquals(AuthState.Restoring, viewModel.authState.value)
    }

    @Test
    fun `a restored session goes straight from restoring to signed in`() = runTest {
        val viewModel = AuthGateViewModel(repository)
        val seen = mutableListOf(viewModel.authState.value)

        repository.state.value = anna
        seen += viewModel.authState.value

        assertEquals(listOf(AuthState.Restoring, anna), seen)
    }

    @Test
    fun `without a saved session the auth flow is shown`() = runTest {
        val viewModel = AuthGateViewModel(repository)

        repository.state.value = AuthState.SignedOut

        assertEquals(AuthState.SignedOut, viewModel.authState.value)
    }

    @Test
    fun `signing out returns to the auth flow`() = runTest {
        repository.state.value = anna
        val viewModel = AuthGateViewModel(repository)

        viewModel.signOut()

        assertEquals(1, repository.signOutCalls)
        assertEquals(AuthState.SignedOut, viewModel.authState.value)
    }
}
