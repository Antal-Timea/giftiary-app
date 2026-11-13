package app.giftiary.data.sync

import android.util.Log
import app.giftiary.data.local.entity.PhotoTransferKind
import app.giftiary.data.photos.GiftPhotoSync
import app.giftiary.data.photos.PhotoTransferResult
import app.giftiary.data.remote.RemoteAccount
import app.giftiary.data.remote.RemoteAccounts
import app.giftiary.data.remote.RemoteException
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.user.ActiveOwner
import app.giftiary.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
 * being retried here. A run that settles a conflict asks for a full sync, since only a pull brings
 * in the backend's row; it does not wake anything, so it is this drain's pull or a later one's.
 * Pushing first gives queued local changes their turn before the backend's data is read; the pull
 * leaves every entity with queued work alone, and queues nothing, so no push follows it. Nothing
 * asks for a drain from inside one, so drains never feed themselves.
 *
 * A drain belongs to the account active when it starts and ends if another becomes active; each
 * run and the pull use a token captured for that account just before them.
 *
 * Background work syncs through the same drains: [syncAndAwait] asks for one and waits for its
 * [DrainOutcome]. A local change also asks [SyncScheduler] for a background catch-up, before the
 * drain is woken, so the change still reaches the backend if this process goes away first.
 *
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
    private val scheduler: SyncScheduler,
    private val photos: GiftPhotoSync,
    @ApplicationScope private val scope: CoroutineScope,
) : SyncRequester, FullSyncRequester {

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private val fullSyncWanted = AtomicBoolean(false)
    private val lock = Any()
    private var started = false

    // Waiting for the next drain to claim them; guarded by lock, like claiming them.
    private val waiters = mutableListOf<CompletableDeferred<DrainOutcome>>()

    /** Asked for by the repositories after every committed local change. */
    override fun requestSync() {
        // The commit and the scheduling are not one transaction: if the process dies in between,
        // the next account-ready sync or the periodic job pushes the change instead. The change is
        // saved either way, so a scheduling failure must not reach the caller as a failed save.
        try {
            scheduler.requestCatchUp()
        } catch (e: Exception) {
            Log.w(TAG, "Could not schedule background sync: ${e::class.simpleName}")
        }
        wake()
    }

    override fun requestFullSync() {
        // Set before the wake-up, so whichever drain the wake-up starts sees it.
        fullSyncWanted.set(true)
        wake()
    }

    /**
     * Asks for a drain ([full]: with a pull) and waits for its outcome. Only a drain that starts
     * after this call can answer it: one already running has claimed its waiters, and this call's
     * wake-up guarantees the next. Several callers may share one drain. Cancelling the wait leaves
     * the drain alone.
     */
    suspend fun syncAndAwait(full: Boolean): DrainOutcome {
        val outcome = CompletableDeferred<DrainOutcome>()
        synchronized(lock) {
            waiters += outcome
            if (full) fullSyncWanted.set(true)
        }
        wake()
        try {
            return outcome.await()
        } finally {
            synchronized(lock) { waiters.remove(outcome) }
        }
    }

    private fun wake() {
        requests.trySend(Unit)
    }

    /** Called once from the Application; later calls do nothing. */
    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
        }
        scope.launch {
            activeOwner.ownerId.filterNotNull().collect {
                scheduler.schedulePeriodic()
                requestFullSync()
            }
        }
        // A new token mostly matters to work refused for the old one; it is no reason to pull, nor
        // a local change to schedule background work for.
        scope.launch { accounts.accessTokenChanges.collect { wake() } }
        scope.launch {
            for (request in requests) {
                // Claimed before the drain reads anything, so it answers exactly these waiters.
                val claimed = synchronized(lock) { waiters.toList().also { waiters.clear() } }
                var outcome = DrainOutcome.TRANSIENT
                try {
                    outcome = drain()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Nothing is lost: the queue is untouched here and the next request tries again.
                    Log.w(TAG, "Sync drain failed: ${e::class.simpleName}")
                } finally {
                    claimed.forEach { it.complete(outcome) }
                }
            }
        }
    }

    private suspend fun drain(): DrainOutcome {
        // A drain is one account's: one run's result never decides a run for another. An account
        // opened meanwhile gets its own drain, from the request its opening made.
        val owner = activeOwner.ownerId.value ?: return DrainOutcome.NO_OWNER
        // Photos before rows: the runner holds back a gift whose photo is not uploaded yet.
        val uploads = photoStep(owner, PhotoTransferKind.UPLOAD) { photos.upload(it) } ?: return DrainOutcome.SESSION_UNUSABLE
        val lastRun = when (val push = push(owner)) {
            is PushPhase.Stopped -> return push.outcome
            is PushPhase.Finished -> push.lastRun
        }
        // Objects of replaced or removed photos, once the rows no longer naming them are pushed.
        // A refused token here is left to the next drain, like work the push left.
        val deletions = photoStep(owner, PhotoTransferKind.DELETE) { photos.deleteUnused(it) }
        photos.pruneLocal(owner)
        val photoFailures = uploads.transientFailures + (deletions?.transientFailures ?: 0)
        return pull(owner) ?: when {
            lastRun.transientFailures > 0 || photoFailures > 0 -> DrainOutcome.TRANSIENT
            lastRun.deferred -> DrainOutcome.DEFERRED
            else -> DrainOutcome.DONE
        }
    }

    private sealed interface PushPhase {
        data class Stopped(val outcome: DrainOutcome) : PushPhase
        data class Finished(val lastRun: SyncRunResult) : PushPhase
    }

    /** Pushes the queue in up to [MAX_RUNS_PER_DRAIN] runs, unless a run is stopped or cannot start. */
    private suspend fun push(owner: UUID): PushPhase {
        var lastRun: SyncRunResult? = null
        for (run in 1..MAX_RUNS_PER_DRAIN) {
            if (activeOwner.ownerId.value != owner) return PushPhase.Stopped(DrainOutcome.OWNER_CHANGED)
            // The token, unlike the account, may change between runs, so it is captured for each.
            val account = capture(owner) ?: return PushPhase.Stopped(DrainOutcome.SESSION_UNUSABLE)
            val result = runner.run(account)
            lastRun = result
            Log.d(
                TAG,
                "Run $run: pushed ${result.pushed}, settled ${result.settled}, stopped ${result.stoppedBecause}, " +
                    "needs attention ${result.needsAttention}",
            )
            // A settled conflict's entity takes the backend's row only through a pull: this
            // drain's, unless the push stops, and then a later drain's.
            if (result.conflictsSettled > 0) fullSyncWanted.set(true)
            when (result.stoppedBecause) {
                null -> Unit
                StopReason.SESSION_REQUIRED -> return PushPhase.Stopped(DrainOutcome.SESSION_UNUSABLE)
                StopReason.PERMISSION -> return PushPhase.Stopped(DrainOutcome.NOT_PERMITTED)
                StopReason.OWNER_CHANGED -> return PushPhase.Stopped(DrainOutcome.OWNER_CHANGED)
                // Never to this coordinator's single consumer; a later drain would find it free.
                StopReason.ALREADY_RUNNING -> return PushPhase.Stopped(DrainOutcome.TRANSIENT)
            }
            if (!result.workRemains || result.settled == 0) break
        }
        return PushPhase.Finished(checkNotNull(lastRun))
    }

    /**
     * Pulls once if a full sync was asked for. A full sync whose pull could not run for want of a
     * usable session stays asked for: the next request, e.g. after a new token, pulls.
     *
     * Returns what kept the pull from applying, or null if it applied or was not asked for.
     */
    private suspend fun pull(owner: UUID): DrainOutcome? {
        if (!fullSyncWanted.get()) return null
        if (activeOwner.ownerId.value != owner) return DrainOutcome.OWNER_CHANGED
        // Taken before anything else can happen, so a full sync asked for from here on, e.g. by
        // another account becoming ready, is left for the drain its request starts.
        if (!fullSyncWanted.getAndSet(false)) return null
        // An account opens before asking for its full sync, so if the request just taken was
        // another account's, the owner has changed by now: it is given back.
        if (activeOwner.ownerId.value != owner) {
            fullSyncWanted.set(true)
            return DrainOutcome.OWNER_CHANGED
        }
        val account = capture(owner)
        if (account == null) {
            fullSyncWanted.set(true)
            return DrainOutcome.SESSION_UNUSABLE
        }
        return when (val result = puller.pull(account)) {
            is PullResult.Applied -> {
                Log.d(
                    TAG,
                    "Pull: inserted ${result.inserted}, updated ${result.updated}, unchanged ${result.unchanged}, " +
                        "skipped queued ${result.skippedQueued}, stale ${result.skippedStale}, " +
                        "local only ${result.skippedLocalOnly}",
                )
                null
            }
            PullResult.OwnerChanged -> {
                Log.d(TAG, "Pull: owner changed")
                DrainOutcome.OWNER_CHANGED
            }
            is PullResult.Failed -> {
                Log.w(TAG, "Pull failed: ${result.failure}")
                if (result.failure in SESSION_FAILURES) fullSyncWanted.set(true)
                when (result.failure) {
                    in SESSION_FAILURES -> DrainOutcome.SESSION_UNUSABLE
                    RemoteFailure.FORBIDDEN -> DrainOutcome.NOT_PERMITTED
                    RemoteFailure.NETWORK, RemoteFailure.SERVER -> DrainOutcome.TRANSIENT
                    else -> DrainOutcome.DONE
                }
            }
        }
    }

    /**
     * A photo step as [owner], or null if there is no usable session for it. A token is captured
     * only when there is photo work, so a drain without any reads no more tokens.
     */
    private suspend fun photoStep(
        owner: UUID,
        kind: PhotoTransferKind,
        step: suspend (RemoteAccount) -> PhotoTransferResult,
    ): PhotoTransferResult? {
        if (!photos.hasPending(owner, kind)) return PhotoTransferResult(0, sessionRefused = false)
        val account = capture(owner) ?: return null
        return step(account).takeUnless { it.sessionRefused }
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
