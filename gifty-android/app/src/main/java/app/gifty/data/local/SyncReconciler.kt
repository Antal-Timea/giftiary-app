package app.gifty.data.local

import androidx.room.withTransaction
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.GiftIdeaDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Applies remote facts to Room once sync has established them. Makes no remote calls itself.
 */
class SyncReconciler @Inject constructor(
    private val database: GiftyDatabase,
    private val operations: SyncOperationDao,
    private val people: PersonDao,
    private val occasions: OccasionDao,
    private val giftIdeas: GiftIdeaDao,
    private val gifts: GiftDao,
) {

    /**
     * Records that the backend committed [mutationId], the entity's mutation in flight, at
     * [resultingVersion] (proven by the write's response or by the mutation ledger).
     *
     * In one transaction the entity adopts that version and becomes known to the backend, and the
     * in-flight mutation is resolved:
     * - if it is still the entity's latest change, the operation is done and removed;
     * - if a newer local change is pending, that change stays queued with its own mutation id,
     *   now as an update of the existing remote row, against [resultingVersion].
     *
     * The entity's content is never touched. In particular, the commit does not prove that the
     * backend holds the current local content: it holds whatever [mutationId] carried, and only
     * the pending newer change, once sent, brings it up to date.
     *
     * Anything that does not match the operation's in-flight slot for this owner (a late,
     * repeated or foreign result) changes nothing and returns [CommitOutcome.STALE].
     */
    suspend fun applyCommitted(
        ownerUserId: UUID,
        entityType: SyncEntityType,
        entityId: UUID,
        mutationId: UUID,
        resultingVersion: Long,
    ): CommitOutcome {
        require(resultingVersion >= 1) { "Impossible backend version $resultingVersion" }
        return database.withTransaction {
            val operation = operations.findForEntity(entityType, entityId)
            if (operation == null || operation.ownerUserId != ownerUserId || operation.inFlightMutationId != mutationId) {
                return@withTransaction CommitOutcome.STALE
            }
            // Nothing may change an entity's version while a mutation of it is in flight, and that
            // mutation was sent against the current version; so its commit is exactly the next one.
            val deletedAt = adoptVersion(entityType, entityId, ownerUserId, resultingVersion)

            if (operation.mutationId == mutationId) {
                operations.delete(operation)
                return@withTransaction CommitOutcome.COMPLETED
            }
            // The newer change keeps its mutation id and queue position. The row now exists
            // remotely, so a pending create continues as an update, or as the deletion if the
            // entity was deleted meanwhile. Attempts so far were sends of the committed mutation.
            val next = when (operation.operation) {
                SyncOperation.CREATE -> if (deletedAt != null) SyncOperation.DELETE else SyncOperation.UPDATE
                SyncOperation.UPDATE, SyncOperation.DELETE -> operation.operation
            }
            operations.update(
                operation.copy(
                    operation = next,
                    inFlightMutationId = null,
                    attemptCount = 0,
                    lastAttemptAt = null,
                    lastErrorCode = null,
                ),
            )
            CommitOutcome.NEWER_MUTATION_PENDING
        }
    }

    /** Moves the entity to [version] and marks it known to the backend; returns its tombstone. */
    private suspend fun adoptVersion(entityType: SyncEntityType, id: UUID, ownerUserId: UUID, version: Long): Instant? {
        val adopted = when (entityType) {
            SyncEntityType.PERSON -> people.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.OCCASION -> occasions.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.GIFT_IDEA -> giftIdeas.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.GIFT -> gifts.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.WISHLIST, SyncEntityType.WISH -> throw IllegalArgumentException("$entityType is not synced")
        }
        // The queue proved this mutation is in flight for this entity, so a mismatch here means
        // the local state or the reported version is wrong; nothing is applied.
        check(adopted == 1) { "$entityType $id is not at version ${version - 1}" }
        return when (entityType) {
            SyncEntityType.PERSON -> people.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.OCCASION -> occasions.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.GIFT_IDEA -> giftIdeas.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.GIFT -> gifts.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.WISHLIST, SyncEntityType.WISH -> null
        }
    }
}

enum class CommitOutcome {
    /** The committed mutation was the entity's latest change; nothing is pending for it any more. */
    COMPLETED,

    /** A newer local change is still pending and will be sent against the adopted version. */
    NEWER_MUTATION_PENDING,

    /** The mutation was not in flight for this owner's entity; nothing changed. */
    STALE,
}
