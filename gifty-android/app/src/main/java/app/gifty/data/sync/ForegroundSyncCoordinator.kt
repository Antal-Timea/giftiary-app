package app.gifty.data.sync

import android.util.Log
import app.gifty.data.remote.RemoteAccounts
import app.gifty.data.remote.RemoteException
import app.gifty.data.user.ActiveOwner
import app.gifty.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes the active account's queue while the app runs, whenever something asks for it: a local
 * change, the account becoming ready, the app coming to the foreground or a new access token.
 *
 * Requests share one slot. It is emptied before a drain starts, so a request made during a drain
 * is never lost: it leads to exactly one more drain afterwards, however many arrive. A drain is at
 * most [MAX_RUNS_PER_DRAIN] runs of [SyncRunner]; a second run follows only one that settled
 * something and left work, so failures that may pass later wait for the next request instead of
 * being retried here. Nothing asks for a drain from inside one, so drains never feed themselves.
 *
 * Every run is for the account active when it starts, with a token captured for it just then.
 * Nothing here is needed for correctness: the queue holds all pending work, and is pushed again
 * on the next request, e.g. when the account is ready after a restart.
 */
@Singleton
class ForegroundSyncCoordinator @Inject constructor(
    private val activeOwner: ActiveOwner,
    private val accounts: RemoteAccounts,
    private val runner: SyncRunner,
    @ApplicationScope private val scope: CoroutineScope,
) : SyncRequester {

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val lock = Any()
    private var started = false

    override fun requestSync() {
        requests.trySend(Unit)
    }

    /** Called once from the Application; later calls do nothing. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch { activeOwner.ownerId.filterNotNull().collect { requestSync() } }
        scope.launch { accounts.accessTokenChanges.collect { requestSync() } }
        scope.launch {
            for (request in requests) {
                try {
                    drain()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing is lost: the queue is untouched here and the next request tries again.
                    Log.w(TAG, "Sync drain failed: ${e::class.simpleName}")
                }
            }
        }
    }

    private suspend fun drain() {
        for (run in 1..MAX_RUNS_PER_DRAIN) {
            val owner = activeOwner.ownerId.value ?: return
            val account = try {
                accounts.capture(owner)
            } catch (e: RemoteException) {
                Log.w(TAG, "No account to sync: ${e.failure}")
                return
            }
            val result = runner.run(account)
            Log.d(
                TAG,
                "Run $run: pushed ${result.pushed}, settled ${result.settled}, stopped ${result.stoppedBecause}, " +
                    "needs attention ${result.needsAttention}",
            )
            if (result.stoppedBecause != null || !result.workRemains || result.settled == 0) return
        }
    }

    private companion object {
        const val TAG = "ForegroundSync"

        // One run already pushes up to SyncRunner.MAX_PUSHES operations. A newer change that a run
        // leaves behind was made after the drain took its request, so that change's own request
        // starts another drain; the second run is mainly for work beyond the first run's budget.
        const val MAX_RUNS_PER_DRAIN = 2
    }
}
