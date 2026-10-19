package app.giftiary.data.user

import android.util.Log
import app.giftiary.data.auth.RecoveryGate
import app.giftiary.data.reminders.ReminderSignOut
import app.giftiary.data.sync.SyncScheduler
import app.giftiary.di.ApplicationScope
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.model.AuthUser
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.AuthRepository
import app.giftiary.domain.repository.OwnerSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whose data is open, following the signed-in account. The only writer of [ownerId].
 *
 * Every change of account closes the previous one first, so the owner is never set while the
 * state still names another account, and the same account reported again keeps its data open
 * untouched.
 */
@Singleton
class OwnerSession @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler,
    private val reminderSignOut: ReminderSignOut,
    private val recoveryGate: RecoveryGate,
    @ApplicationScope private val scope: CoroutineScope,
) : ActiveOwner, AccountSession {

    private val _ownerId = MutableStateFlow<UUID?>(null)
    override val ownerId: StateFlow<UUID?> = _ownerId.asStateFlow()

    private val _state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Restoring)
    override val state: StateFlow<OwnerSessionState> = _state.asStateFlow()

    // Guards everything below and every change of owner or state.
    private val lock = Any()
    private var latestAuth: AuthState = AuthState.Restoring
    private var started = false

    /** Called once from the Application; later calls do nothing. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch { authRepository.authState.collect(::onAuthState) }
        // Its release lets a signed-in account open; while it holds, onAuthState keeps it closed.
        scope.launch { recoveryGate.isActive.collect { synchronized(lock) { onAuthStateLocked(latestAuth) } } }
    }

    override fun signOut() {
        // Close first: the network sign-out can take a while, and the data must disappear now.
        synchronized(lock) { closeLocked(OwnerSessionState.Restoring) }
        // Background sync is the signed-out account's no longer; the next account schedules its own.
        syncScheduler.cancelAll()
        reminderSignOut.onSignOut()
        scope.launch {
            try {
                authRepository.signOut()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Sign-out failed: ${e::class.simpleName}")
            }
            // Normally the signed-out state follows. If the session survived, the same account is
            // still signed in, and its auth state will not be reported again: open it again.
            val stillSignedIn = authRepository.currentUser()
            synchronized(lock) {
                val auth = latestAuth
                if (
                    stillSignedIn != null && auth is AuthState.SignedIn && auth.user.id == stillSignedIn.id &&
                    _state.value == OwnerSessionState.Restoring
                ) {
                    onAuthStateLocked(auth)
                }
            }
        }
    }

    private fun onAuthState(auth: AuthState) = synchronized(lock) {
        latestAuth = auth
        onAuthStateLocked(auth)
    }

    private fun onAuthStateLocked(auth: AuthState) {
        when (auth) {
            AuthState.Restoring -> closeLocked(OwnerSessionState.Restoring)
            AuthState.SignedOut -> closeLocked(OwnerSessionState.SignedOut)
            is AuthState.SignedIn -> {
                // A session that only exists to set a new password opens nothing: no data, no
                // sync, no reminders, until the recovery is done or given up. Read now, not as
                // last seen, so a sign-in from the exchange itself already finds it closed.
                if (recoveryGate.isActive.value) {
                    val closed = _state.value == OwnerSessionState.Restoring && _ownerId.value == null
                    if (!closed) closeLocked(OwnerSessionState.Restoring)
                    return
                }
                val alreadyOpen = (_state.value as? OwnerSessionState.Ready)?.user?.id == auth.user.id &&
                    _ownerId.value == auth.user.id
                // The same account reported again (e.g. the SDK reloading its session when the app
                // returns from the background) must not close and reopen its data.
                if (!alreadyOpen) openLocked(auth.user)
            }
        }
    }

    private fun openLocked(user: AuthUser) {
        closeLocked(OwnerSessionState.Restoring)
        _ownerId.value = user.id
        _state.value = OwnerSessionState.Ready(user)
    }

    /** Owner first, so nothing is ever shown for an owner that is no longer active. */
    private fun closeLocked(state: OwnerSessionState) {
        _ownerId.value = null
        _state.value = state
    }

    private companion object {
        const val TAG = "OwnerSession"
    }
}
