package app.giftiary.data.sync

/**
 * What one drain of [ForegroundSyncCoordinator] found, as far as background work needs to know.
 * Decided only from what the drain saw, never from whether work is merely left in the queue: work
 * waiting on a parent, refused values or unclear evidence do not get better by trying again.
 */
enum class DrainOutcome {
    /** No account was open. */
    NO_OWNER,

    /** The account changed during the drain. */
    OWNER_CHANGED,

    /** No usable session: no token to capture, or the backend refused it. */
    SESSION_UNUSABLE,

    /** The backend refused the account's writes or reads. */
    NOT_PERMITTED,

    /** The network or the server failed a push or the pull; a later attempt may get through. */
    TRANSIENT,

    /** Another drain can push more at once: the push budget ran out, or a newer change was left. */
    DEFERRED,

    /** Nothing a further attempt now could change. */
    DONE,
}
