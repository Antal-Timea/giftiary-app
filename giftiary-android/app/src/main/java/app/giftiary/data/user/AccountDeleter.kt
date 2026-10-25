package app.giftiary.data.user

import android.util.Log
import app.giftiary.data.local.dao.AccountDataDao
import app.giftiary.data.reminders.ReminderNotifier
import app.giftiary.data.reminders.ReminderPreferences
import app.giftiary.data.reminders.ReminderSignOut
import app.giftiary.data.remote.AccountDeletionRemote
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.sync.SyncScheduler
import app.giftiary.di.ApplicationScope
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.repository.AccountDeletion
import app.giftiary.domain.repository.AccountDeletionException
import app.giftiary.domain.repository.AccountDeletionFailure
import app.giftiary.domain.repository.AccountDeletionState
import app.giftiary.domain.repository.AuthRepository
import app.giftiary.domain.repository.OwnerSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deletes the signed-in account: the server first, while its session still works, then this
 * device. [AccountDeletionMarker] is set before the request and cleared last, so a deletion the
 * server may have done is always finished, by the next start if the app stops in between.
 *
 * Finishing on this device: the account is closed (and stays closed), its sync and reminders stop,
 * its reminder choices and rows are deleted, and its session is forgotten without the network.
 */
@Singleton
class AccountDeleter @Inject constructor(
    private val ownerSession: OwnerSession,
    private val authRepository: AuthRepository,
    private val remote: AccountDeletionRemote,
    private val marker: AccountDeletionMarker,
    private val accountData: AccountDataDao,
    private val syncScheduler: SyncScheduler,
    private val reminderSignOut: ReminderSignOut,
    private val reminderPreferences: ReminderPreferences,
    private val reminderNotifier: ReminderNotifier,
    @ApplicationScope private val scope: CoroutineScope,
) : AccountDeletion {

    private val _state = MutableStateFlow<AccountDeletionState>(
        if (marker.owner() != null) AccountDeletionState.Finishing else AccountDeletionState.Idle,
    )
    override val state: StateFlow<AccountDeletionState> = _state.asStateFlow()

    // One deletion step at a time, whether started by the user, a retry or the auth state.
    private val mutex = Mutex()
    private var running: Deferred<Unit>? = null
    private var started = false

    /**
     * Called once from the Application; later calls do nothing. Finishes a deletion an earlier
     * process left unfinished, trying again whenever the auth state changes until it is done.
     */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        val owner = marker.owner() ?: return
        scope.launch { authRepository.authState.first { it != AuthState.Restoring && finish(owner) } }
    }

    override suspend fun deleteAccount() {
        // In the application's scope: the outcome must not depend on the screen that asked staying.
        val deletion = synchronized(this) {
            running?.takeIf { it.isActive } ?: scope.async { deleteSignedInAccount() }.also { running = it }
        }
        deletion.await()
    }

    override fun retry() {
        val owner = marker.owner() ?: return
        if (_state.value !is AccountDeletionState.FinishFailed) return
        scope.launch { finish(owner) }
    }

    override fun onDeletedShown() {
        _state.update { if (it == AccountDeletionState.Deleted) AccountDeletionState.Idle else it }
    }

    private suspend fun deleteSignedInAccount(): Unit = mutex.withLock {
        val owner = (ownerSession.state.value as? OwnerSessionState.Ready)?.user?.id
            ?: throw AccountDeletionException(AccountDeletionFailure.SERVER)
        marker.set(owner)
        try {
            remote.deleteAccount(owner)
        } catch (e: CancellationException) {
            // The request may have reached the server: the marker stays, and the next start finishes.
            throw e
        } catch (e: RemoteException) {
            // Not deleted as far as anyone can tell; a retry by the user is idempotent either way.
            marker.clear()
            throw AccountDeletionException(failureOf(e))
        }
        ownerSession.closeDeletedAccount(owner)
        finishLocally(owner)
        Unit
    }

    /**
     * Completes [owner]'s deletion if it is still pending. With the account's session still in
     * place the server is asked again, which succeeds for an account already deleted. Without it,
     * the server's deletion is taken as done: the SDK drops a session only when it is signed out,
     * here only by finishing, or when the server rejects its refresh token, which it does for every
     * deleted account. A failed request proves nothing and leaves everything as it is.
     *
     * @return whether nothing is left to do.
     */
    private suspend fun finish(owner: UUID): Boolean = mutex.withLock {
        if (marker.owner() != owner) return true
        _state.value = AccountDeletionState.Finishing
        ownerSession.closeDeletedAccount(owner)
        val auth = authRepository.authState.first { it != AuthState.Restoring }
        if (auth is AuthState.SignedIn && auth.user.id == owner) {
            try {
                remote.deleteAccount(owner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RemoteException) {
                _state.value = AccountDeletionState.FinishFailed(failureOf(e))
                return false
            }
        }
        finishLocally(owner)
    }

    private suspend fun finishLocally(owner: UUID): Boolean {
        _state.value = AccountDeletionState.Finishing
        try {
            // Another account may have signed in since (only after an earlier process stopped half
            // way); its session, sync and reminders are its own and stay.
            val auth = authRepository.authState.first { it != AuthState.Restoring }
            val otherAccount = auth is AuthState.SignedIn && auth.user.id != owner
            if (otherAccount) {
                reminderNotifier.cancel(owner)
            } else {
                syncScheduler.cancelAll()
                reminderSignOut.onSignOut()
            }
            reminderPreferences.forget(owner)
            accountData.purge(owner)
            if (!otherAccount) authRepository.clearLocalSession()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Each step can simply run again; the marker keeps the account closed until they all did.
            Log.w(TAG, "Finishing the account deletion failed: ${e::class.simpleName}")
            _state.value = AccountDeletionState.FinishFailed(AccountDeletionFailure.SERVER)
            return false
        }
        marker.clear()
        _state.value = AccountDeletionState.Deleted
        return true
    }

    private fun failureOf(e: RemoteException) =
        if (e.failure == RemoteFailure.NETWORK) AccountDeletionFailure.NETWORK else AccountDeletionFailure.SERVER

    private companion object {
        const val TAG = "AccountDeleter"
    }
}
