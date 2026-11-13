package app.giftiary.data.sync

import app.giftiary.data.local.dao.SyncOperationDao
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperationEntity
import app.giftiary.data.photos.GiftPhotoSync
import app.giftiary.data.remote.RemoteAccount
import app.giftiary.data.remote.RemoteFailure
import app.giftiary.data.user.ActiveOwner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes one account's queue in one bounded run. [SyncPusher] owns everything about a single
 * operation; this decides which operations to push, in what order, and when to stop.
 *
 * - Candidates are the unblocked operations, plus REMOTE_NEWER blocks left by earlier app
 *   versions, which the push settles as conflicts without sending anything.
 * - Parents first ([TYPE_ORDER]); within a type, in-flight mutations first, then oldest first.
 * - An operation waits until every parent it references, and a gift's photo, exists remotely. A
 *   parent settled earlier in the run lets its children through in the same run.
 * - Each operation is pushed at most once per run and at most [MAX_PUSHES] in all. A newer change
 *   left by a settled mutation waits for the next run, so continued editing cannot keep a run going.
 * - A failure holds back only its operation and its children. Only a session, account or
 *   permission failure ends the run.
 *
 * Nothing is blocked here: only an operation's own push decides that, on proof, never on attempts
 * or elapsed time. Runs are serial to save duplicate work; correctness does not depend on it.
 */
@Singleton
class SyncRunner @Inject constructor(
    private val operations: SyncOperationDao,
    private val dependencies: SyncDependencies,
    private val pusher: SyncPusher,
    private val activeOwner: ActiveOwner,
    private val photos: GiftPhotoSync,
) {

    private val running = Mutex()

    suspend fun run(account: RemoteAccount): SyncRunResult {
        val owner = account.ownerUserId
        if (!running.tryLock()) return summarize(account, Progress(), StopReason.ALREADY_RUNNING)
        try {
            val candidates = queue(account).filter { it.blockedReason == null || it.isBlockedConflict() }.sortedWith(PUSH_ORDER)
            val progress = Progress()
            var stoppedBecause: StopReason? = null
            for (operation in candidates) {
                currentCoroutineContext().ensureActive()
                if (progress.pushed == MAX_PUSHES) {
                    // Candidates are left that this run may not push; the next one can.
                    progress.deferred = true
                    break
                }
                if (activeOwner.ownerId.value != owner) {
                    stoppedBecause = StopReason.OWNER_CHANGED
                    break
                }
                if (operation.blockedReason == null &&
                    dependencies.unknownParents(owner, operation.entityType, operation.entityId).isNotEmpty()
                ) {
                    continue
                }
                // Like a parent: the row may not name a photo the backend does not have yet.
                if (photos.isUploadPending(owner, operation.entityType, operation.entityId)) continue

                progress.pushed++
                when (val outcome = pusher.push(account, operation.id)) {
                    PushOutcome.Completed -> progress.settled++
                    PushOutcome.NewerChangePending -> {
                        progress.settled++
                        // The newer change waits for the next run, which can push it at once.
                        progress.deferred = true
                    }
                    PushOutcome.ConflictSettled -> {
                        progress.settled++
                        progress.conflictsSettled++
                    }
                    // A blocked conflict that stays blocked was not settled now.
                    is PushOutcome.Blocked -> if (operation.blockedReason == null) progress.settled++
                    PushOutcome.Stale -> Unit
                    is PushOutcome.Unresolved -> when (outcome.failure) {
                        RemoteFailure.FORBIDDEN -> stoppedBecause = StopReason.PERMISSION
                        // Transport and server trouble that a later attempt can get past; nothing else is.
                        RemoteFailure.NETWORK, RemoteFailure.SERVER -> progress.transientFailures++
                        else -> Unit
                    }
                    is PushOutcome.SessionRequired -> stoppedBecause = StopReason.SESSION_REQUIRED
                }
                if (stoppedBecause != null) break
            }
            return summarize(account, progress, stoppedBecause)
        } finally {
            running.unlock()
        }
    }

    private fun SyncOperationEntity.isBlockedConflict() =
        blockedReason == BlockReason.REMOTE_NEWER.name && inFlightMutationId == null

    private suspend fun queue(account: RemoteAccount): List<SyncOperationEntity> =
        operations.getAllForOwner(account.ownerUserId).filter { it.entityType in PUSHED_TYPES }

    private class Progress {
        var pushed = 0
        var settled = 0
        var conflictsSettled = 0
        var transientFailures = 0
        var deferred = false
    }

    /** The run's counters, with what is left read from the queue as it is now. */
    private suspend fun summarize(account: RemoteAccount, progress: Progress, stoppedBecause: StopReason?): SyncRunResult {
        val queue = queue(account)
        val queued = queue.associateBy { EntityRef(it.entityType, it.entityId) }
        val needsAttention = queue.any { it.blockedReason != null } ||
            // Values refused while an older copy may still be pending: never blocked automatically.
            queue.any { it.inFlightMutationId != null && it.lastErrorCode == RemoteFailure.CONSTRAINT_VIOLATION.name } ||
            queue.any { operation ->
                operation.blockedReason == null &&
                    dependencies.unknownParents(account.ownerUserId, operation.entityType, operation.entityId).any { parent ->
                        // A parent the backend lacks that nothing will push: blocked, or not queued at all.
                        queued[parent]?.blockedReason != null || parent !in queued
                    }
            }
        return SyncRunResult(
            pushed = progress.pushed,
            settled = progress.settled,
            conflictsSettled = progress.conflictsSettled,
            transientFailures = progress.transientFailures,
            deferred = progress.deferred,
            stoppedBecause = stoppedBecause,
            workRemains = queue.any { it.blockedReason == null },
            needsAttention = needsAttention,
        )
    }

    companion object {
        const val MAX_PUSHES = 100

        // Parents before children: the backend's foreign keys need each referenced row first.
        private val TYPE_ORDER = listOf(
            SyncEntityType.PERSON,
            SyncEntityType.OCCASION,
            SyncEntityType.GIFT_IDEA,
            SyncEntityType.GIFT,
            SyncEntityType.WISHLIST,
            SyncEntityType.WISH,
        )
        private val PUSHED_TYPES = TYPE_ORDER.toSet()

        private val PUSH_ORDER = compareBy<SyncOperationEntity>(
            { TYPE_ORDER.indexOf(it.entityType) },
            { if (it.inFlightMutationId != null) 0 else 1 },
            { it.createdAt },
            // Only to make the order total; ids carry no meaning.
            { it.id.toString() },
        )
    }
}
