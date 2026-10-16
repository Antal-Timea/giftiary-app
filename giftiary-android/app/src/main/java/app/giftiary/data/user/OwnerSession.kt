package app.giftiary.data.user

import android.util.Log
import app.giftiary.data.local.LegacyOwnershipClaim
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
import kotlinx.coroutines.Job
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
 * A newly signed-in account is prepared before anything of it is opened: data created before
 * accounts existed is handed over to it first, and only then does the account become the owner.
 * Every change of account passes through "no owner", so no account ever sees another's data, and
 * the same account reported again keeps its data open untouched.
 */
@Singleton
class OwnerSession @Inject constructor(
    private val authRepository: AuthRepository,
    private val legacyOwnerStore: LegacyOwnerStore,
    private val legacyClaim: LegacyOwnershipClaim,
    private val syncScheduler: SyncScheduler,
    private val reminderSignOut: ReminderSignOut,
    @ApplicationScope private val scope: CoroutineScope,
) : ActiveOwner, AccountSession {

    private val _ownerId = MutableStateFlow<UUID?>(null)
    override val ownerId: StateFlow<UUID?> = _ownerId.asStateFlow()

    private val _state = MutableStateFlow<OwnerSessionState>(OwnerSessionState.Restoring)
    override val state: StateFlow<OwnerSessionState> = _state.asStateFlow()

    // Guards everything below and every change of owner or state. Each close or new preparation
    // starts a new generation; a preparation may only publish in the generation it started in, so
    // one that was overtaken (e.g. by another account) can never open its account afterwards.
    private val lock = Any()
    private var latestAuth: AuthState = AuthState.Restoring
    private var generation = 0L
    private var preparation: Job? = null
    private var started = false

    /** Called once from the Application; later calls do nothing. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch { authRepository.authState.collect(::onAuthState) }
    }

    override fun retry() = synchronized(lock) {
        val failed = _state.value as? OwnerSessionState.Failed ?: return
        val auth = latestAuth as? AuthState.SignedIn ?: return
        if (auth.user.id == failed.user.id) prepareLocked(auth.user)
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
            // still signed in, and its auth state will not be reported again: prepare it again.
            val stillSignedIn = authRepository.currentUser()
            synchronized(lock) {
                val auth = latestAuth
                if (
                    stillSignedIn != null && auth is AuthState.SignedIn && auth.user.id == stillSignedIn.id &&
                    _state.value == OwnerSessionState.Restoring && preparation == null
                ) {
                    prepareLocked(auth.user)
                }
            }
        }
    }

    private fun onAuthState(auth: AuthState) = synchronized(lock) {
        latestAuth = auth
        when (auth) {
            AuthState.Restoring -> closeLocked(OwnerSessionState.Restoring)
            AuthState.SignedOut -> closeLocked(OwnerSessionState.SignedOut)
            is AuthState.SignedIn -> {
                val alreadyOpen = (_state.value as? OwnerSessionState.Ready)?.user?.id == auth.user.id &&
                    _ownerId.value == auth.user.id
                // The same account reported again (e.g. the SDK reloading its session when the app
                // returns from the background) must not close and reopen its data.
                if (!alreadyOpen) prepareLocked(auth.user)
            }
        }
    }

    private fun prepareLocked(user: AuthUser) {
        closeLocked(OwnerSessionState.Restoring)
        val preparedIn = generation
        preparation = scope.launch {
            try {
                val legacyOwnerId = legacyOwnerStore.existingLegacyOwnerIdOrNull()
                if (legacyOwnerId != null && legacyOwnerId != user.id) {
                    legacyClaim.claimLegacyData(legacyOwnerId, user.id)
                }
                updateIfCurrent(preparedIn) {
                    _ownerId.value = user.id
                    _state.value = OwnerSessionState.Ready(user)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Preparing account data failed: ${e::class.simpleName}")
                updateIfCurrent(preparedIn) { _state.value = OwnerSessionState.Failed(user) }
            }
        }
    }

    /** Owner first, so nothing is ever shown for an owner that is no longer active. */
    private fun closeLocked(state: OwnerSessionState) {
        preparation?.cancel()
        preparation = null
        generation++
        _ownerId.value = null
        _state.value = state
    }

    private inline fun updateIfCurrent(preparedIn: Long, update: () -> Unit) = synchronized(lock) {
        if (generation != preparedIn) return
        preparation = null
        update()
    }

    private companion object {
        const val TAG = "OwnerSession"
    }
}
