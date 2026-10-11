package app.gifty.data.sync

import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperationEntity
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.user.ActiveOwner
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pushes one account's queue, one operation at a time, in one bounded run. Everything about a
 * single operation is [SyncPusher]'s; this only decides which operations to push, in what order,
 * and when to stop.
 *
 * - Candidates are the owner's unblocked operations when the run starts. Changes made later are
 *   the next run's, but every decision reads the current state of the entities.
 * - Order: by entity type, parents first (Person, Occasion, GiftIdea, Gift); within a type,
 *   mutations still in flight first, then oldest first.
 * - An operation is pushed only once every parent its entity references is known remotely, so a
 *   child never waits on the network for a parent the backend may not have. A parent settled
 *   earlier in the run lets its children through in the same run.
 * - Each operation is pushed at most once per run, and at most [MAX_PUSHES] in all. A newer change
 *   left by a settled mutation waits for the next run, so continued editing cannot keep a run going.
 * - A failure only holds back its own operation and its children; only a failure of the session,
 *   the active account or the account's permissions ends the run.
 *
 * Nothing is ever blocked here, and neither attempts nor time are taken as evidence: an
 * operation's own push decides that, on proof. Runs are serial: a run started while another is in
 * progress returns at once, which saves duplicate work (correctness never depended on it).
 */
@Singleton
class SyncRunner @Inject constructor(
    private val operations: SyncOperationDao,
    private val dependencies: SyncDependencies,
    private val pusher: SyncPusher,
    private val activeOwner: ActiveOwner,
) {

    private val running = Mutex()

    suspend fun run(account: RemoteAccount): SyncRunResult {
        val owner = account.ownerUserId
        if (!running.tryLock()) return summarize(account, pushed = 0, settled = 0, StopReason.ALREADY_RUNNING)
        try {
            val candidates = queue(account).filter { it.blockedReason == null }.sortedWith(PUSH_ORDER)
            var pushed = 0
            var settled = 0
            var stoppedBecause: StopReason? = null
            for (operation in candidates) {
                currentCoroutineContext().ensureActive()
                if (pushed == MAX_PUSHES) break
                if (activeOwner.ownerId.value != owner) {
                    stoppedBecause = StopReason.OWNER_CHANGED
                    break
                }
                if (dependencies.unknownParents(owner, operation.entityType, operation.entityId).isNotEmpty()) continue

                pushed++
                when (val outcome = pusher.push(account, operation.id)) {
                    PushOutcome.Completed, PushOutcome.NewerChangePending, is PushOutcome.Blocked -> settled++
                    PushOutcome.Stale -> Unit
                    is PushOutcome.Unresolved -> if (outcome.failure == RemoteFailure.FORBIDDEN) {
                        stoppedBecause = StopReason.PERMISSION
                    }
                    is PushOutcome.SessionRequired -> stoppedBecause = StopReason.SESSION_REQUIRED
                }
                if (stoppedBecause != null) break
            }
            return summarize(account, pushed, settled, stoppedBecause)
        } finally {
            running.unlock()
        }
    }

    /** The owner's queue of entities that sync. */
    private suspend fun queue(account: RemoteAccount): List<SyncOperationEntity> =
        operations.getAllForOwner(account.ownerUserId).filter { it.entityType in PUSHED_TYPES }

    /** The run's counters, with what is left read from the queue as it is now. */
    private suspend fun summarize(account: RemoteAccount, pushed: Int, settled: Int, stoppedBecause: StopReason?): SyncRunResult {
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
            pushed = pushed,
            settled = settled,
            stoppedBecause = stoppedBecause,
            workRemains = queue.any { it.blockedReason == null },
            needsAttention = needsAttention,
        )
    }

    companion object {
        const val MAX_PUSHES = 100

        private val TYPE_ORDER = listOf(SyncEntityType.PERSON, SyncEntityType.OCCASION, SyncEntityType.GIFT_IDEA, SyncEntityType.GIFT)
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
