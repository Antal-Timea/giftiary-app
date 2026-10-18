package app.giftiary.data.auth

import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AuthError
import app.giftiary.testing.FakeAuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class PasswordRecoveryTest {

    private val auth = FakeAuthRepository().apply { state.value = AuthState.SignedOut }
    private var signOuts = 0

    private fun TestScope.recovery() =
        PasswordRecovery(auth, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))

    private fun code(value: String = "one-time-code") = RecoveryLink.Code(value)

    // Signs out the way AccountSession does: the session ends a little later.
    private val signOut: () -> Unit = {
        signOuts++
        auth.state.value = AuthState.SignedOut
    }

    @Test
    fun `a link is exchanged with the gate already closed, then waits for a new password`() = runTest {
        val recovery = recovery()
        val gateWhenExchanged = mutableListOf<Boolean>()
        auth.gate = CompletableDeferred()

        recovery.start(code())
        gateWhenExchanged += recovery.isActive.value
        auth.gate!!.complete(Unit)

        assertEquals(listOf(true), gateWhenExchanged)
        assertEquals(listOf("one-time-code"), auth.exchangeCalls)
        assertEquals(RecoveryState.ChoosingPassword(), recovery.state.value)
        assertTrue(recovery.isActive.value)
    }

    @Test
    fun `a new password releases the gate and is confirmed once`() = runTest {
        val recovery = recovery()
        recovery.start(code())

        recovery.setPassword("new-password-1")

        assertEquals(listOf("new-password-1"), auth.setPasswordCalls)
        assertEquals(RecoveryState.Idle, recovery.state.value)
        assertFalse(recovery.isActive.value)
        assertTrue(recovery.passwordChanged.value)
        recovery.onPasswordChangedShown()
        assertFalse(recovery.passwordChanged.value)
    }

    @Test
    fun `while someone is signed in the link is not used, and the gate stays open`() = runTest {
        auth.state.value = AuthState.SignedIn(AuthUser(UUID.randomUUID(), "anna@example.com"))
        val recovery = recovery()

        recovery.start(code())

        assertEquals(RecoveryState.Failed(RecoveryFailure.SIGNED_IN, afterExchange = false), recovery.state.value)
        assertTrue("the single-use code stays unused", auth.exchangeCalls.isEmpty())
        assertFalse("the signed-in account is not closed", recovery.isActive.value)

        recovery.leave(signOut)
        assertEquals(RecoveryState.Idle, recovery.state.value)
        assertEquals("staying signed in signs nobody out", 0, signOuts)
    }

    @Test
    fun `a malformed link fails without touching any session`() = runTest {
        auth.state.value = AuthState.SignedIn(AuthUser(UUID.randomUUID(), "anna@example.com"))
        val recovery = recovery()

        recovery.start(RecoveryLink.Invalid)

        assertEquals(RecoveryState.Failed(RecoveryFailure.LINK_INVALID, afterExchange = false), recovery.state.value)
        assertFalse(recovery.isActive.value)
        recovery.leave(signOut)
        assertEquals(0, signOuts)
        assertEquals(RecoveryState.Idle, recovery.state.value)
    }

    @Test
    fun `a used, expired or foreign link is invalid and cannot be retried`() = runTest {
        val recovery = recovery()
        auth.exchangeFailure = AuthError.LINK_INVALID

        recovery.start(code())
        assertEquals(RecoveryState.Failed(RecoveryFailure.LINK_INVALID), recovery.state.value)

        auth.exchangeFailure = null
        recovery.retry()
        assertEquals("the code was dropped", 1, auth.exchangeCalls.size)
    }

    @Test
    fun `a network failure keeps the code for one more try`() = runTest {
        val recovery = recovery()
        auth.exchangeFailure = AuthError.NETWORK_UNAVAILABLE

        recovery.start(code())
        assertEquals(RecoveryState.Failed(RecoveryFailure.NETWORK), recovery.state.value)

        auth.exchangeFailure = null
        recovery.retry()
        assertEquals(listOf("one-time-code", "one-time-code"), auth.exchangeCalls)
        assertEquals(RecoveryState.ChoosingPassword(), recovery.state.value)
    }

    @Test
    fun `a refused password keeps the recovery waiting, with the reason`() = runTest {
        val recovery = recovery()
        recovery.start(code())

        listOf(AuthError.WEAK_PASSWORD, AuthError.SAME_PASSWORD, AuthError.NETWORK_UNAVAILABLE).forEach { error ->
            auth.setPasswordFailure = error
            recovery.setPassword("new-password-1")
            assertEquals(RecoveryState.ChoosingPassword(error), recovery.state.value)
            assertTrue(recovery.isActive.value)
        }
    }

    @Test
    fun `a lost session while setting the password is an invalid link`() = runTest {
        val recovery = recovery()
        recovery.start(code())
        auth.setPasswordFailure = AuthError.LINK_INVALID

        recovery.setPassword("new-password-1")

        assertEquals(RecoveryState.Failed(RecoveryFailure.LINK_INVALID), recovery.state.value)
        assertTrue("its session may still be there", recovery.isActive.value)
    }

    @Test
    fun `giving up signs the recovery session out normally and only then opens the gate`() = runTest {
        val recovery = recovery()
        recovery.start(code())
        var gateDuringSignOut: Boolean? = null

        recovery.leave {
            gateDuringSignOut = recovery.isActive.value
            signOut()
        }

        assertEquals(true, gateDuringSignOut)
        assertEquals(1, signOuts)
        assertEquals(RecoveryState.Idle, recovery.state.value)
        assertFalse(recovery.isActive.value)
        assertFalse(recovery.passwordChanged.value)
    }

    @Test
    fun `the gate holds until the sign-out has really happened`() = runTest {
        val recovery = recovery()
        recovery.start(code())

        // A sign-out still on its way: the session is there for now.
        recovery.leave { signOuts++ }
        assertEquals(RecoveryState.Leaving, recovery.state.value)
        assertTrue(recovery.isActive.value)

        auth.state.value = AuthState.SignedOut
        assertEquals(RecoveryState.Idle, recovery.state.value)
        assertFalse(recovery.isActive.value)
    }

    @Test
    fun `a second link while one recovery is under way is ignored`() = runTest {
        val recovery = recovery()
        recovery.start(code("first"))

        recovery.start(code("second"))

        assertEquals(listOf("first"), auth.exchangeCalls)
        assertEquals(RecoveryState.ChoosingPassword(), recovery.state.value)
    }

    @Test
    fun `a new process starts with nothing of an earlier recovery`() = runTest {
        recovery().start(code())

        val restarted = recovery()

        assertEquals(RecoveryState.Idle, restarted.state.value)
        assertFalse(restarted.isActive.value)
        restarted.retry()
        assertEquals("nothing to retry with", 1, auth.exchangeCalls.size)
    }

    @Test
    fun `the code never shows itself in text`() {
        assertFalse(code("secret-code").toString().contains("secret-code"))
    }
}
