package app.giftiary.presentation.auth

import app.giftiary.data.auth.PasswordRecovery
import app.giftiary.data.auth.RecoveryLink
import app.giftiary.data.auth.RecoveryState
import app.giftiary.data.onboarding.OnboardingPreferences
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountDeletionState
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import app.giftiary.domain.model.InviteToken
import app.giftiary.presentation.sharing.PendingJoin
import app.giftiary.presentation.sharing.PendingWishlistJoin
import app.giftiary.testing.FakeAccountDeletion
import app.giftiary.testing.FakeAuthRepository
import app.giftiary.testing.MainDispatcherRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

// Robolectric for the onboarding record, kept in the app's preferences.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuthGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
    private val deletion = FakeAccountDeletion()
    private val pendingJoin = PendingWishlistJoin()
    private var freshInstall = true
    private val onboarding by lazy { onboardingOnThisDevice() }
    private val viewModel by lazy { AuthGateViewModel(session, recovery, deletion, pendingJoin, onboarding) }

    // A new instance reads what an earlier one stored, as after a restart.
    private fun onboardingOnThisDevice() = OnboardingPreferences(ApplicationProvider.getApplicationContext(), freshInstall)

    @Test
    fun `a new installation opens signed out with the introduction, once`() {
        session.state.value = OwnerSessionState.SignedOut
        assertTrue(viewModel.showOnboarding.value)

        viewModel.onOnboardingDone()

        assertFalse(viewModel.showOnboarding.value)
        assertFalse("not again after a restart", onboardingOnThisDevice().pending.value)
    }

    @Test
    fun `an updated app never shows the introduction, although nothing was recorded before it existed`() {
        freshInstall = false
        session.state.value = OwnerSessionState.SignedOut

        assertFalse(viewModel.showOnboarding.value)
    }

    @Test
    fun `an account opened here ends the introduction, so signing out later goes straight to sign-in`() {
        session.state.value = OwnerSessionState.Ready(alice)
        viewModel.showOnboarding

        session.state.value = OwnerSessionState.SignedOut

        assertFalse(viewModel.showOnboarding.value)
        assertFalse(onboardingOnThisDevice().pending.value)
    }

    @Test
    fun `a deleted account does not bring the introduction back for the next one`() {
        session.state.value = OwnerSessionState.Ready(alice)
        viewModel.showOnboarding

        deletion.state.value = AccountDeletionState.Deleted
        viewModel.onAccountDeletedShown()
        session.state.value = OwnerSessionState.SignedOut

        assertFalse(viewModel.showOnboarding.value)
    }

    @Test
    fun `an invite link goes straight to sign-in, leaving the introduction for later`() {
        session.state.value = OwnerSessionState.SignedOut
        pendingJoin.offer(PendingJoin.Invite(InviteToken.of("a".repeat(43))!!))

        assertFalse(viewModel.showOnboarding.value)
        assertTrue("not completed by it", onboarding.pending.value)
    }

    @Test
    fun `a recovery link is shown over the introduction, which stays for afterwards`() {
        session.state.value = OwnerSessionState.SignedOut
        assertTrue(viewModel.showOnboarding.value)

        recovery.start(RecoveryLink.Code("code"))

        // The gate draws recovery before anything signed out, so the introduction waits under it.
        assertEquals(RecoveryState.ChoosingPassword(), viewModel.recoveryState.value)
        assertTrue(viewModel.showOnboarding.value)
    }

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

    @Test
    fun `a finished deletion forgets a waiting invite, so the next account does not redeem it`() {
        val token = InviteToken.of("a".repeat(43))!!
        pendingJoin.offer(PendingJoin.Invite(token))
        viewModel.deletionState

        deletion.state.value = AccountDeletionState.Deleted

        assertNull(pendingJoin.take())
        viewModel.onAccountDeletedShown()
        assertEquals(AccountDeletionState.Idle, viewModel.deletionState.value)
    }

    @Test
    fun `retrying an unfinished deletion goes to the deletion`() {
        viewModel.retryDeletion()

        assertEquals(1, deletion.retryCalls)
    }
}
