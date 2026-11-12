package app.giftiary.data.user

import android.util.Log
import app.giftiary.data.remote.DeviceProofRemote
import app.giftiary.data.remote.RemoteException
import app.giftiary.di.ApplicationScope
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.repository.AuthRepository
import app.giftiary.domain.repository.OwnerSessionState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes the data of an account that was deleted on another device, once the server confirms it.
 *
 * Losing a session proves nothing: supabase-kt drops it alike when the account is gone, when its
 * sessions were revoked, and on some temporary refusals (429), and it does not say which. So an
 * account whose data stays here without its session is only marked unverified, and the server is
 * asked about it with this device's proof (see [DeviceProofs]). Only a definite "not live" for a
 * proof the server had registered removes the data, through [LocalAccountPurge]. Any failure to get
 * that answer changes nothing and is tried again at the next start, return to the foreground or
 * change of account. The account signing in again marks it verified.
 *
 * Never touches the account that is open, one signed in at all (e.g. held closed by a password
 * recovery), or one this device is deleting itself ([AccountDeleter] finishes that one).
 */
@Singleton
class DeletedAccountCheck @Inject constructor(
    private val ownerSession: OwnerSession,
    private val authRepository: AuthRepository,
    private val remote: DeviceProofRemote,
    private val proofs: DeviceProofs,
    private val deletionMarker: AccountDeletionMarker,
    private val localPurge: LocalAccountPurge,
    @ApplicationScope private val scope: CoroutineScope,
) : DeletedAccountCheckRequester {

    // One registration or check at a time.
    private val mutex = Mutex()
    private var started = false

    /** Called once from the Application; later calls do nothing. */
    fun start() {
        synchronized(this) {
            if (started) return
            started = true
        }
        scope.launch { ownerSession.state.collect(::onOwnerState) }
    }

    /** E.g. the app came to the foreground: a chance to finish what failed earlier, e.g. offline. */
    override fun requestCheck() {
        scope.launch {
            (ownerSession.state.value as? OwnerSessionState.Ready)?.let { register(it.user.id) }
            checkUnverified()
        }
    }

    private fun onOwnerState(state: OwnerSessionState) {
        when (state) {
            OwnerSessionState.Restoring -> return
            OwnerSessionState.SignedOut -> proofs.lastOwner()?.let(proofs::markUnverified)
            is OwnerSessionState.Ready -> {
                // Another account's data was open last, here or in an earlier process.
                proofs.lastOwner()?.takeIf { it != state.user.id }?.let(proofs::markUnverified)
                proofs.markVerified(state.user.id)
                proofs.setLastOwner(state.user.id)
            }
        }
        scope.launch {
            (state as? OwnerSessionState.Ready)?.let { register(it.user.id) }
            checkUnverified()
        }
    }

    private suspend fun register(owner: UUID) = mutex.withLock {
        if (proofs.secret(owner) != null) return@withLock
        val secret = DeviceProofs.newSecret()
        try {
            remote.register(owner, DeviceProofs.hashOf(secret))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteException) {
            // Offline, not yet deployed, or over the limit: without a proof the data is just kept.
            Log.w(TAG, "Registering the device proof failed: ${e.failure}")
            return@withLock
        }
        // Saved only now, so a saved secret is always one the server registered.
        proofs.saveSecret(owner, secret)
    }

    private suspend fun checkUnverified() = mutex.withLock {
        for (owner in proofs.unverified()) {
            if (!mayRemove(owner)) continue
            val secret = proofs.secret(owner) ?: continue
            val live = try {
                remote.isLive(secret)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RemoteException) {
                // No answer is not "not live": nothing changes, and the next chance asks again.
                Log.w(TAG, "Checking an account failed: ${e.failure}")
                return@withLock
            }
            // A live account keeps its data, and stays unverified so a later deletion is still seen.
            if (live || !mayRemove(owner)) continue
            ownerSession.closeDeletedAccount(owner)
            try {
                localPurge.purge(owner)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Still unverified, with its secret: the next chance asks and purges again.
                Log.w(TAG, "Removing a deleted account failed: ${e::class.simpleName}")
                return@withLock
            }
        }
    }

    // Any session of the account, open or held closed, means it exists; the open account has one.
    private suspend fun mayRemove(owner: UUID): Boolean {
        if (deletionMarker.owner() == owner) return false
        val auth = authRepository.authState.first { it != AuthState.Restoring }
        return !(auth is AuthState.SignedIn && auth.user.id == owner)
    }

    private companion object {
        const val TAG = "DeletedAccountCheck"
    }
}
