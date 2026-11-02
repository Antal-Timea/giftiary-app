package app.giftiary.presentation.settings

import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.testing.FakeAccountDeletion
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val anna = AuthUser(UUID.randomUUID(), "anna@example.com")
    private val session = object : AccountSession {
        override val state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Ready(anna))
        override fun signOut() = Unit
    }

    private val deletion = FakeAccountDeletion()

    private fun viewModel() = SettingsViewModel(session, deletion)

    @Test
    fun `the signed-in email is the user's own, for the account group`() {
        assertEquals("anna@example.com", viewModel().email)
    }

    @Test
    fun `deleting asks first, and cancelling deletes nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.onDeleteAccount()
        assertEquals(DeleteAccountDialog(), viewModel.state.value.deleteDialog)

        viewModel.onDeleteAccountDismiss()
        assertNull(viewModel.state.value.deleteDialog)
        assertEquals(0, deletion.deleteCalls)
    }

    @Test
    fun `a running deletion cannot be dismissed or started again`() = runTest {
        val gate = CompletableDeferred<Unit>()
        deletion.gate = gate
        val viewModel = viewModel()
        viewModel.onDeleteAccount()

        viewModel.onDeleteAccountConfirm()
        viewModel.onDeleteAccountConfirm()
        viewModel.onDeleteAccountDismiss()
        viewModel.onDeleteAccount()

        assertEquals(DeleteAccountDialog(deleting = true), viewModel.state.value.deleteDialog)
        assertEquals(1, deletion.deleteCalls)

        // Success: the app leaves this screen as the account closes; until then it stays busy.
        gate.complete(Unit)
        assertEquals(DeleteAccountDialog(deleting = true), viewModel.state.value.deleteDialog)
    }

    @Test
    fun `a failed deletion stays in the dialog and can be tried again`() = runTest {
        deletion.failure = AccountDeletionFailure.NETWORK
        val viewModel = viewModel()
        viewModel.onDeleteAccount()

        viewModel.onDeleteAccountConfirm()
        assertEquals(DeleteAccountDialog(failure = AccountDeletionFailure.NETWORK), viewModel.state.value.deleteDialog)

        viewModel.onDeleteAccountConfirm()
        assertEquals(2, deletion.deleteCalls)
        assertEquals(DeleteAccountDialog(deleting = true), viewModel.state.value.deleteDialog)
    }

    @Test
    fun `a failed deletion can also be cancelled`() = runTest {
        deletion.failure = AccountDeletionFailure.SERVER
        val viewModel = viewModel()
        viewModel.onDeleteAccount()
        viewModel.onDeleteAccountConfirm()

        viewModel.onDeleteAccountDismiss()

        assertNull(viewModel.state.value.deleteDialog)
        assertEquals(1, deletion.deleteCalls)
    }

    @Test
    fun `confirming without the dialog does nothing`() = runTest {
        val viewModel = viewModel()

        viewModel.onDeleteAccountConfirm()

        assertEquals(0, deletion.deleteCalls)
    }
}
