package app.giftiary.data.sync

import app.giftiary.data.auth.BackgroundSession
import app.giftiary.data.auth.BackgroundSessionResult
import app.giftiary.domain.repository.AccountSession
import app.giftiary.domain.repository.OwnerSessionState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One background sync job, as [BackgroundSyncWorker] runs it: wait for the account's data to open,
 * make its stored session usable, then let [ForegroundSyncCoordinator] drain, and decide whether
 * the job is worth repeating.
 *
 * Only transport, server or session-restore trouble is retried, and only while the job has run
 * fewer than [RETRY_LIMIT] times before; anything else ends it. Work stuck in the queue is left to
 * the attention view and to the next local change, foreground start or periodic run.
 */
class BackgroundSync internal constructor(
    private val accountState: StateFlow<OwnerSessionState>,
    private val prepareSession: suspend () -> BackgroundSessionResult,
    private val syncAndAwait: suspend (full: Boolean) -> DrainOutcome,
) {

    @Inject
    constructor(accountSession: AccountSession, session: BackgroundSession, coordinator: ForegroundSyncCoordinator) :
        this(accountSession.state, session::prepare, coordinator::syncAndAwait)

    /** [full] also pulls; [attempt] is how often the job ran before. True if it should run again. */
    suspend fun run(full: Boolean, attempt: Int): Boolean {
        val state = withTimeoutOrNull(OPEN_TIMEOUT) { accountState.first { it !is OwnerSessionState.Restoring } }
            ?: return retryUnlessExhausted(attempt)
        if (state !is OwnerSessionState.Ready) return false

        when (prepareSession()) {
            BackgroundSessionResult.READY -> Unit
            BackgroundSessionResult.RETRY_LATER -> return retryUnlessExhausted(attempt)
            // Signed out, or a refresh token the backend refused: the foreground sorts that out.
            BackgroundSessionResult.NO_SESSION, BackgroundSessionResult.REJECTED -> return false
        }

        var withPull = full
        repeat(MAX_DRAINS) {
            val outcome = withTimeoutOrNull(DRAIN_TIMEOUT) { syncAndAwait(withPull) } ?: DrainOutcome.TRANSIENT
            when (outcome) {
                DrainOutcome.TRANSIENT -> return retryUnlessExhausted(attempt)
                // More can be pushed at once; the pull, if any, was this drain's.
                DrainOutcome.DEFERRED -> withPull = false
                DrainOutcome.NO_OWNER,
                DrainOutcome.OWNER_CHANGED,
                DrainOutcome.SESSION_UNUSABLE,
                DrainOutcome.NOT_PERMITTED,
                DrainOutcome.DONE,
                -> return false
            }
        }
        return false
    }

    private fun retryUnlessExhausted(attempt: Int) = attempt < RETRY_LIMIT

    companion object {
        /** Once a job has run this often before, it is not retried again. */
        const val RETRY_LIMIT = 5

        /** Drains one run may wait for while each of them leaves more to push at once. */
        const val MAX_DRAINS = 3

        private val OPEN_TIMEOUT = 60.seconds
        private val DRAIN_TIMEOUT = 5.minutes
    }
}
