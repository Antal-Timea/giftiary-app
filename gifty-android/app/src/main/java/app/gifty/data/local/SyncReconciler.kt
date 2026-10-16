package app.gifty.data.local

import androidx.room.withTransaction
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.GiftIdeaDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.SyncConflictDao
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.dao.WishDao
import app.gifty.data.local.dao.WishlistDao
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncConflictEntity
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.local.entity.SyncOperation
import app.gifty.data.remote.dto.RemoteGiftFields
import app.gifty.data.remote.dto.RemoteGiftIdeaFields
import app.gifty.data.remote.dto.RemoteOccasionFields
import app.gifty.data.remote.dto.RemotePersonFields
import app.gifty.data.remote.dto.RemoteWishFields
import app.gifty.data.remote.dto.RemoteWishlistFields
import app.gifty.data.remote.toRemoteFields
import app.gifty.data.sync.BlockReason
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
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
    private val wishlists: WishlistDao,
    private val wishes: WishDao,
    private val conflicts: SyncConflictDao,
    private val clock: Clock,
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

    /**
     * Settles the entity's operation as a conflict, now that the backend's row is proven to be at
     * [remoteVersion], past [baseVersion], the version every change in the operation is based on:
     * none of them can commit any more.
     *
     * In one transaction the entity's current state, including any change made after
     * [provingMutationId] was sent, is kept in `sync_conflict`, and the operation is removed. The
     * entity itself is not touched: with its operation gone, the next pull brings in the backend's
     * row, with all of the pull's checks.
     *
     * [provingMutationId] is the mutation the evidence was gathered for: the one in flight or, if
     * [wasBlocked], the current one, blocked for this conflict by an earlier version of the app.
     * Unless the operation is still exactly in that state for this owner (a late, repeated or
     * foreign result, or a newer change that unblocked it) nothing changes and false is returned.
     */
    suspend fun settleConflict(
        ownerUserId: UUID,
        entityType: SyncEntityType,
        entityId: UUID,
        provingMutationId: UUID,
        wasBlocked: Boolean,
        baseVersion: Long,
        remoteVersion: Long,
    ): Boolean = database.withTransaction {
        val operation = operations.findForEntity(entityType, entityId)
        if (operation == null || operation.ownerUserId != ownerUserId) return@withTransaction false
        val unchanged = if (wasBlocked) {
            operation.inFlightMutationId == null &&
                operation.mutationId == provingMutationId &&
                operation.blockedReason == BlockReason.REMOTE_NEWER.name
        } else {
            operation.inFlightMutationId == provingMutationId
        }
        if (!unchanged) return@withTransaction false

        val local = snapshot(entityType, entityId, ownerUserId)
        // The version only changes when a mutation is settled, so these mean the local state or
        // the evidence is wrong; nothing is applied.
        check(local.remoteState == RemoteState.SYNCED && local.version == baseVersion) {
            "$entityType $entityId is not synced at version $baseVersion"
        }
        check(remoteVersion > baseVersion) { "Version $remoteVersion is no conflict with $baseVersion" }
        // Keyed by the proving mutation, which is settled only once: a second record for it is a bug.
        conflicts.insert(
            SyncConflictEntity(
                id = provingMutationId,
                ownerUserId = ownerUserId,
                entityType = entityType,
                entityId = entityId,
                mutationId = provingMutationId,
                baseVersion = baseVersion,
                remoteVersion = remoteVersion,
                localSnapshotJson = local.json,
                createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS),
                seenAt = null,
            ),
        )
        operations.delete(operation)
        true
    }

    /**
     * Settles the create of gift [giftId] as a conflict, now that another gift of the owner is
     * proven to hold [sourceGiftIdeaId] on the backend, at [winnerVersion]: the backend allows one
     * gift per idea, so no content of this one can be inserted.
     *
     * In one transaction the gift's current state, including any change made after
     * [provingMutationId] was sent, is kept in `sync_conflict` (with base version 0, which only a
     * create can have), the gift is deleted locally, and its operation is removed. The gift never
     * reached the backend and nothing is queued for it, so it stays local; the next pull brings
     * in the winner.
     *
     * Unless [provingMutationId] is still the gift's mutation in flight for this owner, nothing
     * changes and false is returned.
     */
    suspend fun settleDuplicateGift(
        ownerUserId: UUID,
        giftId: UUID,
        provingMutationId: UUID,
        sourceGiftIdeaId: UUID,
        winnerVersion: Long,
    ): Boolean = database.withTransaction {
        val operation = operations.findForEntity(SyncEntityType.GIFT, giftId)
        if (operation == null || operation.ownerUserId != ownerUserId || operation.inFlightMutationId != provingMutationId) {
            return@withTransaction false
        }
        val gift = checkNotNull(gifts.findById(giftId, ownerUserId)) { "No gift $giftId" }
        // An insert in flight leaves the gift unknown to the backend, and its source never changes.
        check(gift.remoteState == RemoteState.LOCAL_ONLY && gift.version == 0L && gift.sourceGiftIdeaId == sourceGiftIdeaId) {
            "Gift $giftId is not a local create from $sourceGiftIdeaId"
        }
        require(winnerVersion >= 1) { "Impossible backend version $winnerVersion" }
        val now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS)
        conflicts.insert(
            SyncConflictEntity(
                id = provingMutationId,
                ownerUserId = ownerUserId,
                entityType = SyncEntityType.GIFT,
                entityId = giftId,
                mutationId = provingMutationId,
                baseVersion = 0,
                remoteVersion = winnerVersion,
                localSnapshotJson = SNAPSHOT_JSON.encodeToString(RemoteGiftFields.serializer(), gift.toRemoteFields()),
                createdAt = now,
                seenAt = null,
            ),
        )
        // Not queued: the backend never had the gift. Already deleted locally, it stays as it was.
        gifts.softDelete(giftId, ownerUserId, now)
        operations.delete(operation)
        true
    }

    private class Snapshot(val remoteState: RemoteState, val version: Long, val json: String)

    /** The entity's current state as the backend would store it. */
    private suspend fun snapshot(entityType: SyncEntityType, id: UUID, ownerUserId: UUID): Snapshot = when (entityType) {
        SyncEntityType.PERSON -> checkNotNull(people.findById(id, ownerUserId)) { "No person $id" }.let {
            Snapshot(it.remoteState, it.version, SNAPSHOT_JSON.encodeToString(RemotePersonFields.serializer(), it.toRemoteFields()))
        }
        SyncEntityType.OCCASION -> checkNotNull(occasions.findById(id, ownerUserId)) { "No occasion $id" }.let {
            Snapshot(it.remoteState, it.version, SNAPSHOT_JSON.encodeToString(RemoteOccasionFields.serializer(), it.toRemoteFields()))
        }
        SyncEntityType.GIFT_IDEA -> checkNotNull(giftIdeas.findById(id, ownerUserId)) { "No gift idea $id" }.let {
            Snapshot(it.remoteState, it.version, SNAPSHOT_JSON.encodeToString(RemoteGiftIdeaFields.serializer(), it.toRemoteFields()))
        }
        SyncEntityType.GIFT -> checkNotNull(gifts.findById(id, ownerUserId)) { "No gift $id" }.let {
            Snapshot(it.remoteState, it.version, SNAPSHOT_JSON.encodeToString(RemoteGiftFields.serializer(), it.toRemoteFields()))
        }
        SyncEntityType.WISHLIST -> checkNotNull(wishlists.findById(id, ownerUserId)) { "No wishlist $id" }.let {
            Snapshot(it.remoteState, it.version, SNAPSHOT_JSON.encodeToString(RemoteWishlistFields.serializer(), it.toRemoteFields()))
        }
        SyncEntityType.WISH -> checkNotNull(wishes.findById(id, ownerUserId)) { "No wish $id" }.let {
            Snapshot(it.remoteState, it.version, SNAPSHOT_JSON.encodeToString(RemoteWishFields.serializer(), it.toRemoteFields()))
        }
    }

    /** Moves the entity to [version] and marks it known to the backend; returns its tombstone. */
    private suspend fun adoptVersion(entityType: SyncEntityType, id: UUID, ownerUserId: UUID, version: Long): Instant? {
        val adopted = when (entityType) {
            SyncEntityType.PERSON -> people.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.OCCASION -> occasions.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.GIFT_IDEA -> giftIdeas.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.GIFT -> gifts.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.WISHLIST -> wishlists.adoptRemoteVersion(id, ownerUserId, version)
            SyncEntityType.WISH -> wishes.adoptRemoteVersion(id, ownerUserId, version)
        }
        // The queue proved this mutation is in flight for this entity, so a mismatch here means
        // the local state or the reported version is wrong; nothing is applied.
        check(adopted == 1) { "$entityType $id is not at version ${version - 1}" }
        return when (entityType) {
            SyncEntityType.PERSON -> people.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.OCCASION -> occasions.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.GIFT_IDEA -> giftIdeas.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.GIFT -> gifts.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.WISHLIST -> wishlists.findById(id, ownerUserId)?.deletedAt
            SyncEntityType.WISH -> wishes.findById(id, ownerUserId)?.deletedAt
        }
    }
}

/**
 * The format of a conflict's local snapshot: the entity's Remote*Fields, the same JSON a push
 * sends, with every field written out, nulls included, so a later reader needs no defaults to tell
 * a cleared value from a missing one.
 */
private val SNAPSHOT_JSON = Json {
    explicitNulls = true
    encodeDefaults = true
}

enum class CommitOutcome {
    /** The committed mutation was the entity's latest change; nothing is pending for it any more. */
    COMPLETED,

    /** A newer local change is still pending and will be sent against the adopted version. */
    NEWER_MUTATION_PENDING,

    /** The mutation was not in flight for this owner's entity; nothing changed. */
    STALE,
}
