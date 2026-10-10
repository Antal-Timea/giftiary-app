package app.gifty.data.sync

import app.gifty.data.remote.RemoteFailure

/** The result of pushing one operation. It says what happened, not when to try again. */
sealed interface PushOutcome {

    /** The mutation committed and was the entity's latest change; nothing is pending for it. */
    data object Completed : PushOutcome

    /**
     * The mutation is settled (it committed, or proved unable to) and a newer local change is
     * still queued. That change is not pushed as part of this call.
     */
    data object NewerChangePending : PushOutcome

    /** Nothing was done: the operation is gone, is not this account's, or was settled meanwhile. */
    data object Stale : PushOutcome

    /** The mutation can never commit as it is, and stays blocked until the user changes the entity. */
    data class Blocked(val reason: BlockReason) : PushOutcome

    /**
     * The mutation's outcome is not known yet, or the backend could not take it now. It stays in
     * flight and is settled by a later push; [failure] is the last remote failure, if any.
     */
    data class Unresolved(val failure: RemoteFailure?) : PushOutcome

    /** The captured session cannot be used (expired, or another account's); nothing more is tried. */
    data class SessionRequired(val failure: RemoteFailure) : PushOutcome
}
