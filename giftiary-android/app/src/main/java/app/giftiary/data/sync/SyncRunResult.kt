package app.giftiary.data.sync

/**
 * What one bounded run did and what it left. It does not decide whether to run again; a caller
 * may, e.g. while [settled] > 0 and [workRemains].
 */
data class SyncRunResult(
    /** Operations handed to [SyncPusher.push]. */
    val pushed: Int,
    /** Pushes that settled their mutation: completed, superseded by a newer change, blocked, or a conflict. */
    val settled: Int,
    /**
     * Of [settled], the operations settled as conflicts. Their entities still show the local state
     * until a pull brings in the backend's rows.
     */
    val conflictsSettled: Int,
    /**
     * Pushes left unresolved by the network or the server: the only failures a later attempt can
     * get past without anything else changing first.
     */
    val transientFailures: Int = 0,
    /**
     * Whether the next run can push more at once: this one used its whole budget with candidates
     * left, or a push left a newer change behind it. Not merely work remaining, which may be stuck.
     */
    val deferred: Boolean = false,
    /** Why the run ended early, or null if it went through every candidate it could. */
    val stoppedBecause: StopReason?,
    /** Whether any unblocked operation of the owner is still queued. */
    val workRemains: Boolean,
    /**
     * Whether something cannot sync without the user: a blocked operation, a mutation whose values
     * were refused while an older copy may still be pending, or a change waiting on a parent that
     * cannot reach the backend.
     */
    val needsAttention: Boolean,
)

enum class StopReason {
    /** Another run was in progress; this one did nothing. */
    ALREADY_RUNNING,

    /** The captured session can no longer be used. */
    SESSION_REQUIRED,

    /** The app's active account is no longer the one the run was for. */
    OWNER_CHANGED,

    /** The backend refused the account's writes. */
    PERMISSION,
}
