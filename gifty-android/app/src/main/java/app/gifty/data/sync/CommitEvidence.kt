package app.gifty.data.sync

import app.gifty.data.remote.dto.RemoteRow
import app.gifty.data.remote.dto.RemoteSyncMutation
import java.util.UUID

/** What left the outcome of sending a mutation open. */
internal enum class Uncertainty {
    /** No usable answer arrived, or an earlier run ended while the mutation was in flight. */
    NO_ANSWER,

    /** The backend reported a unique key that already exists (PostgreSQL 23505). */
    DUPLICATE,

    /** The conditional update matched no row. */
    NO_MATCH,
}

internal sealed interface Verdict {

    /** The mutation committed, producing [version]. */
    data class Committed(val version: Long) : Verdict

    /** The mutation has not committed and, as sent, never can. */
    data class CannotCommit(val reason: BlockReason) : Verdict

    /** It has not committed, and sending the same mutation again is safe and may succeed. */
    data object MaySend : Verdict

    /** The evidence settles nothing yet. */
    data object Unknown : Verdict
}

/**
 * Judges whether [mutationId] committed, from what the backend shows after an uncertain write.
 * [expectedVersion] is the version an update was sent against, null for an insert.
 *
 * - [ledger] is the mutation's ledger entry, if a lookup found one. It is authoritative: the
 *   backend records it in the same transaction as the write.
 * - [row] is the entity's remote row, read after a ledger lookup missed. A row whose
 *   last_mutation_id is [mutationId] was last written by it, at its resulting version: the same
 *   transaction sets that id, the version and the ledger entry, and every later write must bring
 *   a new mutation id.
 * - If neither proves a commit, [ledger] must come from a second lookup made after [row] was read.
 *   Anything that committed before the row was read is then in it; so what remains judges what
 *   the mutation can still do from now on.
 *
 * Absence of evidence is never taken as failure: [Verdict.CannotCommit] only where the backend's
 * own rules rule a commit out (a version that has moved on, a row that cannot reappear, a key
 * already taken).
 */
internal fun judge(
    uncertainty: Uncertainty,
    mutationId: UUID,
    expectedVersion: Long?,
    ledger: RemoteSyncMutation?,
    row: RemoteRow?,
): Verdict {
    if (ledger != null) return Verdict.Committed(ledger.resultingVersion)
    if (row != null && row.lastMutationId == mutationId) return Verdict.Committed(row.version)

    if (expectedVersion == null) {
        return when {
            // The id exists remotely, but not through this mutation.
            row != null -> Verdict.CannotCommit(BlockReason.REMOTE_ID_TAKEN)
            // The id is free, so the duplicate was another unique key, e.g. a gift's source idea.
            uncertainty == Uncertainty.DUPLICATE -> Verdict.CannotCommit(BlockReason.REJECTED)
            uncertainty == Uncertainty.NO_ANSWER -> Verdict.MaySend
            else -> Verdict.Unknown
        }
    }
    return when {
        // Rows are never hard-deleted and never change owner, so an invisible row stays invisible.
        row == null -> Verdict.CannotCommit(BlockReason.REMOTE_MISSING)
        // Versions only grow, so the update's condition can never match again.
        row.version > expectedVersion -> Verdict.CannotCommit(BlockReason.REMOTE_NEWER)
        row.version == expectedVersion -> when (uncertainty) {
            Uncertainty.NO_ANSWER -> Verdict.MaySend
            // Still at the expected version, so another unique key refused the values.
            Uncertainty.DUPLICATE -> Verdict.CannotCommit(BlockReason.REJECTED)
            // The condition should have matched; nothing can be concluded from this answer.
            Uncertainty.NO_MATCH -> Verdict.Unknown
        }
        // Older than the version this device already had from the backend: not trustworthy.
        else -> Verdict.Unknown
    }
}

/**
 * Why a mutation can never commit as it is. Stored by name as the operation's blocked reason; a
 * new local change unblocks it.
 */
enum class BlockReason {
    /** The backend refused the values: a constraint, or a unique key other than the row's id. */
    REJECTED,

    /** Conflict: the remote row has moved past the version the change was based on. */
    REMOTE_NEWER,

    /** Conflict: the remote row is no longer visible to the account. */
    REMOTE_MISSING,

    /** Conflict: a remote row with this id exists that this change did not create. */
    REMOTE_ID_TAKEN,
}
