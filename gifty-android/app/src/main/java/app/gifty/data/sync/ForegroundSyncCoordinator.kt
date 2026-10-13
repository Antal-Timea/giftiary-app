package app.gifty.data.sync

import android.util.Log
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteAccounts
import app.gifty.data.remote.RemoteException
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.user.ActiveOwner
import app.gifty.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs the active account while the app runs, whenever something asks for it. A local change or a
 * new access token asks for a push; the account becoming ready or the app coming to the foreground
 * asks for a full sync, a push followed by a pull of the backend's data.
 *
 * Requests share one slot. It is emptied before a drain starts, so a request made during a drain
 * is never lost: it leads to exactly one more drain afterwards, however many arrive. That a full
 * sync was asked for is kept apart, in a flag a push request cannot clear, and the flag is taken
 * only just before a pull, so a full sync asked for during a drain is never lost either.
 *
 * A drain is at most [MAX_RUNS_PER_DRAIN] runs of [SyncRunner], then at most one pull if a full
 * sync was asked for and the push was not stopped. A second run follows only one that settled
 * something and left work, so failures that may pass later wait for the next request instead of
 * being retried here. Pushing first gives queued local changes their turn before the backend's
 * data is read; the pull leaves every entity with queued work alone, and queues nothing, so no push
 * follows it. Nothing asks for a drain from inside one, so drains never feed themselves.
 *
 * A drain belongs to the account active when it starts and ends if another becomes active; each
 * run and the pull use a token captured for that account just before them.
 * Nothing here is needed for correctness: the queue holds all pending work, a pull can always be
 * repeated, and both happen again on the next request, e.g. when the account is ready after a
 * restart.
 */
@Singleton
class ForegroundSyncCoordinator @Inject constructor(
    private val activeOwner: ActiveOwner,
    private val accounts: RemoteAccounts,
    private val runner: SyncRunner,
    private val puller: SyncPuller,
    @ApplicationScope private val scope: CoroutineScope,
) : SyncRequester, FullSyncRequester {

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val fullSyncWanted = AtomicBoolean(false)
    private val lock = Any()
    private var started = false

    override fun requestSync() {
        requests.trySend(Unit)
    }

    override fun requestFullSync() {
        // Set before the wake-up, so whichever drain the wake-up starts sees it.
        fullSyncWanted.set(true)
        requests.trySend(Unit)
    }

    /** Called once from the Application; later calls do nothing. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch { activeOwner.ownerId.filterNotNull().collect { requestFullSync() } }
        // A new token mostly matters to work refused for the old one; it is no reason to pull.
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
        // A drain is one account's: one run's result never decides a run for another. An account
        // opened meanwhile gets its own drain, from the request its opening made.
        val owner = activeOwner.ownerId.value ?: return
        if (push(owner)) pull(owner)
    }

    /** Pushes the queue in up to [MAX_RUNS_PER_DRAIN] runs; false if a run was stopped or could not start. */
    private suspend fun push(owner: UUID): Boolean {
        for (run in 1..MAX_RUNS_PER_DRAIN) {
            if (activeOwner.ownerId.value != owner) return false
            // The token, unlike the account, may change between runs, so it is captured for each.
            val account = capture(owner) ?: return false
            val result = runner.run(account)
            Log.d(
                TAG,
                "Run $run: pushed ${result.pushed}, settled ${result.settled}, stopped ${result.stoppedBecause}, " +
                    "needs attention ${result.needsAttention}",
            )
            if (result.stoppedBecause != null) return false
            if (!result.workRemains || result.settled == 0) return true
        }
        return true
    }

    /**
     * Pulls once if a full sync was asked for. A full sync whose pull could not run for want of a
     * usable session stays asked for: the next request, e.g. after a new token, pulls.
     */
    private suspend fun pull(owner: UUID) {
        if (!fullSyncWanted.get() || activeOwner.ownerId.value != owner) return
        // Taken before anything else can happen, so a full sync asked for from here on, e.g. by
        // another account becoming ready, is left for the drain its request starts.
        if (!fullSyncWanted.getAndSet(false)) return
        // An account opens before asking for its full sync, so if the request just taken was
        // another account's, the owner has changed by now: it is given back.
        if (activeOwner.ownerId.value != owner) {
            fullSyncWanted.set(true)
            return
        }
        val account = capture(owner)
        if (account == null) {
            fullSyncWanted.set(true)
            return
        }
        when (val result = puller.pull(account)) {
            is PullResult.Applied -> Log.d(
                TAG,
                "Pull: inserted ${result.inserted}, updated ${result.updated}, unchanged ${result.unchanged}, " +
                    "skipped queued ${result.skippedQueued}, stale ${result.skippedStale}, " +
                    "local only ${result.skippedLocalOnly}",
            )
            PullResult.OwnerChanged -> Log.d(TAG, "Pull: owner changed")
            is PullResult.Failed -> {
                Log.w(TAG, "Pull failed: ${result.failure}")
                if (result.failure in SESSION_FAILURES) fullSyncWanted.set(true)
            }
        }
    }

    private fun capture(owner: UUID): RemoteAccount? = try {
        accounts.capture(owner)
    } catch (e: RemoteException) {
        Log.w(TAG, "No account to sync: ${e.failure}")
        null
    }

    private companion object {
        const val TAG = "ForegroundSync"

        // One run already pushes up to SyncRunner.MAX_PUSHES operations. A newer change that a run
        // leaves behind was made after the drain took its request, so that change's own request
        // starts another drain; the second run is mainly for work beyond the first run's budget.
        const val MAX_RUNS_PER_DRAIN = 2

        val SESSION_FAILURES = setOf(RemoteFailure.UNAUTHENTICATED, RemoteFailure.ACCOUNT_MISMATCH)
    }
}
