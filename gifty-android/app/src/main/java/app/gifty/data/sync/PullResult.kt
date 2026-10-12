package app.gifty.data.sync

import app.gifty.data.remote.RemoteFailure

/** What one pull did. It says what happened, not when to pull again. */
sealed interface PullResult {

    /**
     * The backend's rows were applied, each counted once by what happened to it. Nothing else
     * changed: rows the backend does not have are left as they are.
     */
    data class Applied(
        /** Rows Room did not have. */
        val inserted: Int,
        /** Rows replaced by a newer backend version. */
        val updated: Int,
        /** Rows Room already has at the backend's version. */
        val unchanged: Int,
        /** Rows left alone because a local change of them is queued, whatever its state. */
        val skippedQueued: Int,
        /** Rows Room already has at a newer version, e.g. pushed after the backend was read. */
        val skippedStale: Int,
        /** Rows Room has only locally, which the backend's copy must not replace. */
        val skippedLocalOnly: Int,
    ) : PullResult

    /** The active account changed after the backend was read; nothing was applied. */
    data object OwnerChanged : PullResult

    /** The backend could not be read, or its data could not be used; nothing was applied. */
    data class Failed(val failure: RemoteFailure) : PullResult
}
