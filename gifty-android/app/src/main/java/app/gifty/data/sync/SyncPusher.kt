package app.gifty.data.sync

import androidx.room.withTransaction
import app.gifty.data.local.CommitOutcome
import app.gifty.data.local.GiftyDatabase
import app.gifty.data.local.SyncReconciler
import app.gifty.data.local.dao.GiftDao
import app.gifty.data.local.dao.GiftIdeaDao
import app.gifty.data.local.dao.OccasionDao
import app.gifty.data.local.dao.PersonDao
import app.gifty.data.local.dao.SyncOperationDao
import app.gifty.data.local.entity.RemoteState
import app.gifty.data.local.entity.SyncEntityType
import app.gifty.data.remote.Committed
import app.gifty.data.remote.NoMatch
import app.gifty.data.remote.RemoteAccount
import app.gifty.data.remote.RemoteException
import app.gifty.data.remote.RemoteFailure
import app.gifty.data.remote.RemoteReader
import app.gifty.data.remote.RemoteWriter
import app.gifty.data.remote.UpdateOutcome
import app.gifty.data.remote.dto.RemoteRow
import app.gifty.data.remote.dto.RemoteSyncMutation
import app.gifty.data.remote.toRemoteFields
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import javax.inject.Inject

/**
 * Pushes one queued operation, chosen by the caller, for one captured account: it settles the
 * operation's single unresolved mutation and leaves Room consistent. It never picks other
 * operations and never pushes a newer change that appears meanwhile.
 *
 * Which mutation: the one in flight if there is one (left by a run that ended without knowing the
 * outcome), otherwise the current one, which is marked in flight before anything is sent. A newer
 * change is never sent while an older mutation is unresolved.
 *
 * What is sent: the entity's state when the attempt starts, read in the same transaction that
 * checks the mutation is in flight. Whether it is an insert or an update, and the version an
 * update expects, come from the entity's remote state and version, which nothing changes while a
 * mutation is in flight; the queue's operation may already describe a newer change. A retry of
 * the same mutation may therefore carry newer content than the first send: the backend holds
 * whatever the committed copy carried, and the newer change stays queued to follow.
 *
 * Retries are bounded: at most two sends per call (one after a run that ended in flight), and only
 * when the backend's evidence shows the mutation has not committed and still can. The same
 * mutation id is always reused; none is ever created here.
 *
 * A mutation is only ever settled as unable to commit on proof. Refused values count as proof only
 * for the one copy of a mutation that was ever sent; a missing referenced row never does, since
 * the parent may still arrive. Otherwise the mutation stays in flight, unresolved.
 *
 * A remote row that moved past the version the entity's changes are based on is a conflict: none
 * of them can commit, so the operation is settled as a whole, keeping the local state as a
 * conflict, and the backend's row reaches Room through the next pull. An operation that an earlier
 * version of the app blocked for this is settled the same way, on a fresh read of the row; it is
 * never sent.
 *
 * Network requests are never made inside a Room transaction. Cancellation is not a failure: it
 * leaves the mutation in flight, to be settled by the next push.
 */
class SyncPusher @Inject constructor(
    private val database: GiftyDatabase,
    private val operations: SyncOperationDao,
    private val people: PersonDao,
    private val occasions: OccasionDao,
    private val giftIdeas: GiftIdeaDao,
    private val gifts: GiftDao,
    private val reconciler: SyncReconciler,
    private val reader: RemoteReader,
    private val writer: RemoteWriter,
    private val clock: Clock,
) {

    /** Pushes operation [operationId] of [account]'s owner, using only that captured account. */
    suspend fun push(account: RemoteAccount, operationId: UUID): PushOutcome {
        val mutation = when (val start = start(account.ownerUserId, operationId)) {
            is Start.Claimed -> start.mutation
            is Start.BlockedConflict -> return try {
                settleBlockedConflict(account, start.mutation)
            } catch (e: RemoteException) {
                unresolved(start.mutation, e.failure)
            }
            is Start.Blocked -> return PushOutcome.Blocked(start.reason)
            Start.Gone -> return PushOutcome.Stale
        }
        return try {
            settle(account, mutation)
        } catch (e: RemoteException) {
            unresolved(mutation, e.failure)
        }
    }

    private suspend fun settle(account: RemoteAccount, mutation: InFlight): PushOutcome {
        // A mutation left in flight may have committed already: find out before sending it again.
        var verdict = if (mutation.resumed) inspect(account, mutation, Uncertainty.NO_ANSWER) else Verdict.MaySend
        val maxSends = if (mutation.resumed) 1 else 2
        var sends = 0
        while (verdict == Verdict.MaySend && sends < maxSends) {
            // Only a fresh mutation's first send can be the only copy of it the backend may ever see.
            val onlyCopy = !mutation.resumed && sends == 0
            sends++
            verdict = send(account, mutation, onlyCopy) ?: return PushOutcome.Stale
        }
        return when (verdict) {
            is Verdict.Committed -> committed(mutation, verdict.version)
            is Verdict.CannotCommit -> cannotCommit(mutation, verdict.reason)
            is Verdict.RemoteNewer -> conflict(mutation, verdict.remoteVersion, wasBlocked = false)
            Verdict.MaySend -> unresolved(mutation, RemoteFailure.NETWORK)
            Verdict.Unknown -> unresolved(mutation, null)
        }
    }

    /**
     * Sends the in-flight mutation once and judges the answer; null if it is no longer in flight.
     *
     * [onlyCopy] says no other copy of the mutation can be outstanding: the mutation was claimed
     * fresh by this call (a mutation is claimed fresh only if it was never in flight before, since
     * an in-flight slot is only freed once its mutation committed, was blocked, or was superseded)
     * and nothing of it was sent yet.
     */
    private suspend fun send(account: RemoteAccount, mutation: InFlight, onlyCopy: Boolean): Verdict? {
        val write = beginAttempt(mutation) ?: return null
        val uncertainty = try {
            when (val result = write(account)) {
                is Committed -> return Verdict.Committed(result.version)
                NoMatch -> Uncertainty.NO_MATCH
            }
        } catch (e: RemoteException) {
            when (e.failure) {
                RemoteFailure.NETWORK -> Uncertainty.NO_ANSWER
                RemoteFailure.DUPLICATE -> Uncertainty.DUPLICATE
                // This copy was rolled back because its values are refused. That settles the mutation
                // only if it was the only copy: an earlier one may carry older, valid content and
                // still commit, so otherwise it stays in flight for a later push to look for.
                RemoteFailure.CONSTRAINT_VIOLATION ->
                    if (onlyCopy) return Verdict.CannotCommit(BlockReason.REJECTED) else throw e
                // MISSING_REFERENCE among them: the parent may still arrive, with nothing to change here.
                // Settled by a later push, which first looks for evidence of a commit.
                else -> throw e
            }
        }
        return inspect(account, mutation, uncertainty)
    }

    /** Gathers the backend's evidence on the mutation, in the order [judge] requires. */
    private suspend fun inspect(account: RemoteAccount, mutation: InFlight, uncertainty: Uncertainty): Verdict {
        ledgerEntry(account, mutation)?.let {
            return judge(uncertainty, mutation.mutationId, mutation.expectedVersion, it, row = null)
        }
        val row = remoteRow(account, mutation)
        if (row?.lastMutationId == mutation.mutationId) {
            return judge(uncertainty, mutation.mutationId, mutation.expectedVersion, ledger = null, row)
        }
        val ledgerAfterRow = ledgerEntry(account, mutation)
        return judge(uncertainty, mutation.mutationId, mutation.expectedVersion, ledgerAfterRow, row)
    }

    /**
     * The mutation's ledger entry, which must be for this very entity. A mutation id is used once,
     * so an entry for anything else is a backend inconsistency, never proof: SERVER, unresolved.
     */
    private suspend fun ledgerEntry(account: RemoteAccount, mutation: InFlight): RemoteSyncMutation? =
        reader.findMutation(account, mutation.mutationId)?.also { entry ->
            if (entry.entityId != mutation.entityId || entry.entityType != mutation.entityType.remoteTable()) {
                throw RemoteException(RemoteFailure.SERVER)
            }
        }

    private suspend fun committed(mutation: InFlight, version: Long): PushOutcome =
        when (reconciler.applyCommitted(mutation.ownerUserId, mutation.entityType, mutation.entityId, mutation.mutationId, version)) {
            CommitOutcome.COMPLETED -> PushOutcome.Completed
            CommitOutcome.NEWER_MUTATION_PENDING -> PushOutcome.NewerChangePending
            // Already settled elsewhere; the commit must not lead to another write.
            CommitOutcome.STALE -> PushOutcome.Stale
        }

    /**
     * Settles a mutation that can never commit. If it is still the latest change it is blocked; if
     * the user changed the entity meanwhile, that newer change is left unblocked, to be pushed
     * next. Either way the in-flight slot is freed, in the same transaction.
     */
    private suspend fun cannotCommit(mutation: InFlight, reason: BlockReason): PushOutcome = database.withTransaction {
        val operation = operations.findById(mutation.operationId)
        if (operation == null || operation.inFlightMutationId != mutation.mutationId) return@withTransaction PushOutcome.Stale
        if (operation.mutationId == mutation.mutationId) {
            operations.update(operation.copy(inFlightMutationId = null, blockedReason = reason.name, lastErrorCode = reason.name))
            PushOutcome.Blocked(reason)
        } else {
            // The attempts so far were sends of the settled mutation, not of the newer change.
            operations.update(
                operation.copy(inFlightMutationId = null, attemptCount = 0, lastAttemptAt = null, lastErrorCode = null),
            )
            PushOutcome.NewerChangePending
        }
    }

    /**
     * Settles an operation blocked as REMOTE_NEWER by an earlier version of the app. The block was
     * recorded on proof, while the mutation was in flight, so the mutation never committed; only
     * the version the backend is at now is read. Anything short of a newer row of this very
     * entity leaves the operation blocked.
     */
    private suspend fun settleBlockedConflict(account: RemoteAccount, mutation: InFlight): PushOutcome {
        val row = remoteRow(account, mutation)
        val expectedVersion = mutation.expectedVersion
        val isNewer = row != null && expectedVersion != null &&
            row.id == mutation.entityId && row.ownerUserId == mutation.ownerUserId && row.version > expectedVersion
        if (!isNewer) return PushOutcome.Blocked(BlockReason.REMOTE_NEWER)
        return conflict(mutation, row.version, wasBlocked = true)
    }

    private suspend fun conflict(mutation: InFlight, remoteVersion: Long, wasBlocked: Boolean): PushOutcome {
        val settled = reconciler.settleConflict(
            mutation.ownerUserId, mutation.entityType, mutation.entityId, mutation.mutationId, wasBlocked,
            baseVersion = checkNotNull(mutation.expectedVersion) { "Only an update can conflict" },
            remoteVersion = remoteVersion,
        )
        return if (settled) PushOutcome.ConflictSettled else PushOutcome.Stale
    }

    /** Leaves the mutation in flight, noting why; the next push settles it. */
    private suspend fun unresolved(mutation: InFlight, failure: RemoteFailure?): PushOutcome {
        failure?.let { operations.recordError(mutation.operationId, mutation.mutationId, it.name) }
        return when (failure) {
            RemoteFailure.UNAUTHENTICATED, RemoteFailure.ACCOUNT_MISMATCH -> PushOutcome.SessionRequired(failure)
            else -> PushOutcome.Unresolved(failure)
        }
    }

    // ---- Room side: every step re-checks the queue inside its own transaction ----

    private sealed interface Start {
        data class Claimed(val mutation: InFlight) : Start
        /** Blocked as REMOTE_NEWER by an earlier version of the app; [mutation] is not in flight. */
        data class BlockedConflict(val mutation: InFlight) : Start
        data class Blocked(val reason: BlockReason) : Start
        data object Gone : Start
    }

    /** The mutation being settled. Its kind and expected version stay fixed while it is in flight. */
    private data class InFlight(
        val operationId: UUID,
        val ownerUserId: UUID,
        val entityType: SyncEntityType,
        val entityId: UUID,
        val mutationId: UUID,
        /** The version an update expects; null for an insert. */
        val expectedVersion: Long?,
        /** Whether an earlier run left it in flight with its outcome unknown. */
        val resumed: Boolean,
    )

    /** Picks the mutation to settle and, for a new one, marks it in flight before anything is sent. */
    private suspend fun start(ownerUserId: UUID, operationId: UUID): Start = database.withTransaction {
        val operation = operations.findById(operationId)
        if (operation == null || operation.ownerUserId != ownerUserId) return@withTransaction Start.Gone
        val inFlight = operation.inFlightMutationId
        if (inFlight != null) {
            val local = local(operation.entityType, operation.entityId, ownerUserId)
            return@withTransaction Start.Claimed(
                InFlight(operationId, ownerUserId, operation.entityType, operation.entityId, inFlight, local.expectedVersion, resumed = true),
            )
        }
        operation.blockedReason?.let { reason ->
            if (reason == BlockReason.REMOTE_NEWER.name) {
                val local = local(operation.entityType, operation.entityId, ownerUserId)
                return@withTransaction Start.BlockedConflict(
                    InFlight(
                        operationId, ownerUserId, operation.entityType, operation.entityId, operation.mutationId,
                        local.expectedVersion, resumed = false,
                    ),
                )
            }
            return@withTransaction Start.Blocked(BlockReason.entries.firstOrNull { it.name == reason } ?: BlockReason.REJECTED)
        }
        // Read and checked before the queue changes, so an impossible local state sends nothing.
        val local = local(operation.entityType, operation.entityId, ownerUserId)
        check(operations.markInFlight(operation.id, operation.mutationId) == 1) { "Could not mark ${operation.id} in flight" }
        Start.Claimed(
            InFlight(
                operationId, ownerUserId, operation.entityType, operation.entityId, operation.mutationId,
                local.expectedVersion, resumed = false,
            ),
        )
    }

    /**
     * Records a send of the in-flight mutation and returns the write to make, built from the
     * entity's state read in this same transaction; null if the mutation is no longer in flight.
     */
    private suspend fun beginAttempt(mutation: InFlight): (suspend (RemoteAccount) -> UpdateOutcome)? =
        database.withTransaction {
            val operation = operations.findById(mutation.operationId)
            if (operation == null || operation.inFlightMutationId != mutation.mutationId) return@withTransaction null
            val local = local(mutation.entityType, mutation.entityId, mutation.ownerUserId)
            check(local.expectedVersion == mutation.expectedVersion) {
                "${mutation.entityType} ${mutation.entityId} changed version while ${mutation.mutationId} was in flight"
            }
            operations.recordAttempt(operation.id, mutation.mutationId, Instant.now(clock).truncatedTo(ChronoUnit.MILLIS))
            val write = local.write
            return@withTransaction { account: RemoteAccount -> write(account, mutation.mutationId) }
        }

    /** The entity as it would be sent now. */
    private class Local(
        remoteState: RemoteState,
        version: Long,
        val write: suspend (RemoteAccount, mutationId: UUID) -> UpdateOutcome,
    ) {
        /** Null for an insert. Remote state and version only change when a mutation is settled. */
        val expectedVersion: Long? = when (remoteState) {
            RemoteState.LOCAL_ONLY -> null.also { check(version == 0L) { "A local-only row must be at version 0, not $version" } }
            RemoteState.SYNCED -> version.also { check(version >= 1) { "A row known to the backend has no version" } }
        }
    }

    private suspend fun local(type: SyncEntityType, id: UUID, ownerUserId: UUID): Local = when (type) {
        SyncEntityType.PERSON -> checkNotNull(people.findById(id, ownerUserId)) { "No person $id" }.let { entity ->
            val fields = entity.toRemoteFields()
            Local(entity.remoteState, entity.version) { account, mutationId ->
                if (entity.remoteState == RemoteState.LOCAL_ONLY) {
                    writer.insertPerson(account, id, mutationId, fields)
                } else {
                    writer.updatePerson(account, id, entity.version, mutationId, fields)
                }
            }
        }
        SyncEntityType.OCCASION -> checkNotNull(occasions.findById(id, ownerUserId)) { "No occasion $id" }.let { entity ->
            val fields = entity.toRemoteFields()
            Local(entity.remoteState, entity.version) { account, mutationId ->
                if (entity.remoteState == RemoteState.LOCAL_ONLY) {
                    writer.insertOccasion(account, id, mutationId, fields)
                } else {
                    writer.updateOccasion(account, id, entity.version, mutationId, fields)
                }
            }
        }
        SyncEntityType.GIFT_IDEA -> checkNotNull(giftIdeas.findById(id, ownerUserId)) { "No gift idea $id" }.let { entity ->
            val fields = entity.toRemoteFields()
            Local(entity.remoteState, entity.version) { account, mutationId ->
                if (entity.remoteState == RemoteState.LOCAL_ONLY) {
                    writer.insertGiftIdea(account, id, mutationId, fields)
                } else {
                    writer.updateGiftIdea(account, id, entity.version, mutationId, fields)
                }
            }
        }
        SyncEntityType.GIFT -> checkNotNull(gifts.findById(id, ownerUserId)) { "No gift $id" }.let { entity ->
            val fields = entity.toRemoteFields()
            Local(entity.remoteState, entity.version) { account, mutationId ->
                if (entity.remoteState == RemoteState.LOCAL_ONLY) {
                    writer.insertGift(account, id, mutationId, fields)
                } else {
                    writer.updateGift(account, id, entity.version, mutationId, fields)
                }
            }
        }
        SyncEntityType.WISHLIST, SyncEntityType.WISH -> throw IllegalArgumentException("$type is not synced")
    }

    /** The backend table, as the ledger names it. */
    private fun SyncEntityType.remoteTable(): String = when (this) {
        SyncEntityType.PERSON -> "people"
        SyncEntityType.OCCASION -> "occasions"
        SyncEntityType.GIFT_IDEA -> "gift_ideas"
        SyncEntityType.GIFT -> "gifts"
        SyncEntityType.WISHLIST, SyncEntityType.WISH -> throw IllegalArgumentException("$this is not synced")
    }

    private suspend fun remoteRow(account: RemoteAccount, mutation: InFlight): RemoteRow? = when (mutation.entityType) {
        SyncEntityType.PERSON -> reader.findPerson(account, mutation.entityId)
        SyncEntityType.OCCASION -> reader.findOccasion(account, mutation.entityId)
        SyncEntityType.GIFT_IDEA -> reader.findGiftIdea(account, mutation.entityId)
        SyncEntityType.GIFT -> reader.findGift(account, mutation.entityId)
        SyncEntityType.WISHLIST, SyncEntityType.WISH -> throw IllegalArgumentException("${mutation.entityType} is not synced")
    }
}
