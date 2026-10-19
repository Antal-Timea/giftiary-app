package app.giftiary.data.local

import app.giftiary.data.local.dao.SyncOperationDao
import app.giftiary.data.local.entity.RemoteState
import app.giftiary.data.local.entity.SyncEntityType
import app.giftiary.data.local.entity.SyncOperation
import app.giftiary.data.local.entity.SyncOperationEntity
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
 * Every call is a new logical change and gets a new random mutation id, also when it folds into a
 * pending operation. The mutation already in flight, if any, is never touched here: only sync
 * knows when its outcome is settled.
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
        if (pending == null) {
            dao.insert(newOperation(ownerUserId, entityType, entityId, operation))
            return
        }
        // An entity never changes owner, so neither does its pending operation. A pre-v2 operation
        // without an owner keeps it that way: a write never assigns one.
        check(pending.ownerUserId == null || pending.ownerUserId == ownerUserId) {
            "Pending $entityType operation for $entityId belongs to another owner"
        }
        val coalesced = coalesce(
            pending = pending.operation,
            next = operation,
            existsRemotely = remoteState == RemoteState.SYNCED,
            canBeForgotten = !entityType.canBeReferenced() && pending.inFlightMutationId == null,
        )
        if (coalesced == null) {
            dao.delete(pending)
            return
        }
        // Updating the original row preserves its queue position, e.g. a person's CREATE stays
        // ahead of gifts created for that person later. Earlier failures and a block were about an
        // older payload, so the new change starts afresh.
        dao.update(
            pending.copy(
                operation = coalesced,
                mutationId = UUID.randomUUID(),
                blockedReason = null,
                attemptCount = 0,
                lastAttemptAt = null,
                lastErrorCode = null,
            ),
        )
    }

    private fun newOperation(ownerUserId: UUID, entityType: SyncEntityType, entityId: UUID, operation: SyncOperation) =
        SyncOperationEntity(
            id = UUID.randomUUID(),
            ownerUserId = ownerUserId,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            mutationId = UUID.randomUUID(),
            inFlightMutationId = null,
            blockedReason = null,
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
         * [canBeForgotten] says whether an entity the backend has never seen may simply be dropped
         * when it is deleted: only if no other synced row can reference it and none of its
         * mutations is in flight. Otherwise its tombstone is created remotely, so a child pushed
         * later never points at a parent that does not exist there, and an in-flight create that
         * did commit is still followed by the deletion.
         *
         * Combinations that can only come from a bug (creating an entity twice, changing a deleted
         * one) throw instead of guessing, because a wrong queue state would silently lose data.
         */
        fun coalesce(
            pending: SyncOperation?,
            next: SyncOperation,
            existsRemotely: Boolean,
            canBeForgotten: Boolean,
        ): SyncOperation? = when (pending) {
            null -> next
            SyncOperation.CREATE -> when (next) {
                SyncOperation.UPDATE -> SyncOperation.CREATE
                // A SYNCED entity with a pending CREATE is a stale or inconsistent queue (e.g. an
                // interrupted push); deleting remotely is the safe recovery, not a regular
                // lifecycle transition.
                SyncOperation.DELETE -> when {
                    existsRemotely -> SyncOperation.DELETE
                    canBeForgotten -> null
                    else -> SyncOperation.CREATE
                }
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

/**
 * Whether another synced row may point at this entity: occasions, gift ideas and wishlists at
 * people, ideas and gifts at occasions, gifts at their source idea, wishes at their wishlist.
 * Nothing synced points at a gift or a wish: reservations will refer only to wishes the backend
 * already has, never to one it has never seen.
 */
private fun SyncEntityType.canBeReferenced(): Boolean = when (this) {
    SyncEntityType.PERSON,
    SyncEntityType.OCCASION,
    SyncEntityType.GIFT_IDEA,
    SyncEntityType.WISHLIST,
    -> true
    SyncEntityType.GIFT,
    SyncEntityType.WISH,
    -> false
}
