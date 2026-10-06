package app.gifty.data.local

import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.local.entity.SyncOperationEntity
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/**
 * Records that an entity's remote state needs to change, keeping at most one pending operation
 * per entity. Sync pushes the entity's current Room state, so earlier changes are folded into
 * the pending operation instead of being replayed.
 *
 * Must be called inside the Room transaction that changes the entity, with that entity's owner.
 */
class SyncOperationQueue @Inject constructor(
    private val dao: SyncOperationDao,
    private val clock: Clock,
) {

    suspend fun enqueue(
        ownerUserId: UUID,
        entityType: SyncEntityType,
        entityId: UUID,
        operation: SyncOperation,
        remoteState: RemoteState,
    ) {
        val pending = dao.findForEntity(entityType, entityId)
        // An entity never changes owner, so neither does its pending operation. A pre-v2 operation
        // without an owner keeps it that way: assigning one is the legacy claim's job, not a write's.
        check(pending?.ownerUserId == null || pending.ownerUserId == ownerUserId) {
            "Pending $entityType operation for $entityId belongs to another owner"
        }
        val coalesced = coalesce(
            pending = pending?.operation,
            next = operation,
            existsRemotely = remoteState == RemoteState.SYNCED,
        )
        when {
            pending == null && coalesced != null ->
                dao.insert(newOperation(ownerUserId, entityType, entityId, coalesced))
            pending == null -> Unit
            coalesced == null -> dao.delete(pending)
            // Keeping the original row preserves its queue position, e.g. a person's CREATE
            // stays ahead of gifts created for that person later.
            coalesced == pending.operation -> Unit
            else -> dao.update(
                pending.copy(
                    operation = coalesced,
                    attemptCount = 0,
                    lastAttemptAt = null,
                    lastErrorCode = null,
                ),
            )
        }
    }

    private fun newOperation(ownerUserId: UUID, entityType: SyncEntityType, entityId: UUID, operation: SyncOperation) =
        SyncOperationEntity(
            id = UUID.randomUUID(),
            ownerUserId = ownerUserId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS),
            attemptCount = 0,
            lastAttemptAt = null,
            lastErrorCode = null,
        )

    companion object {

        /**
         * Returns the operation that should be pending after [next] is applied on top of [pending],
         * or null when nothing needs to reach the backend.
         *
         * Combinations that can only come from a bug (creating an entity twice, changing a deleted
         * one) throw instead of guessing, because a wrong queue state would silently lose data.
         */
        fun coalesce(
            pending: SyncOperation?,
            next: SyncOperation,
            existsRemotely: Boolean,
        ): SyncOperation? = when (pending) {
            null -> next
            SyncOperation.CREATE -> when (next) {
                SyncOperation.UPDATE -> SyncOperation.CREATE
                // Normally a pending CREATE means the entity is still LOCAL_ONLY, so nothing is left
                // to sync. A SYNCED entity with a pending CREATE is a stale or inconsistent queue
                // (e.g. an interrupted push); deleting remotely is the safe recovery, not a regular
                // lifecycle transition.
                SyncOperation.DELETE -> if (existsRemotely) SyncOperation.DELETE else null
                SyncOperation.CREATE -> invalid(pending, next)
            }
            SyncOperation.UPDATE -> when (next) {
                SyncOperation.UPDATE -> SyncOperation.UPDATE
                SyncOperation.DELETE -> SyncOperation.DELETE
                SyncOperation.CREATE -> invalid(pending, next)
            }
            SyncOperation.DELETE -> invalid(pending, next)
        }

        private fun invalid(pending: SyncOperation, next: SyncOperation): Nothing =
            throw IllegalStateException("Cannot enqueue $next while $pending is pending")
    }
}
