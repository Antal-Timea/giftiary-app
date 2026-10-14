package app.gifty.data.auth

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionSource
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.CancellationException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlin.time.toKotlinInstant

/**
 * Makes the stored session usable for background sync, for background work only.
 *
 * When the app leaves the foreground, supabase-kt stops refreshing and reports the session as
 * initializing, though it keeps it stored; until the app returns, no access token is available. A
 * worker running in such a process restores the stored session itself, through the SDK's own
 * storage path, and refreshes it once if it is about to expire.
 *
 * Nothing here starts the SDK's automatic refresh: in the background nothing would stop it again,
 * and a failing refresh would then retry every few seconds for as long as the process lives. The
 * process lifecycle keeps owning that: returning to the foreground reloads the session as usual.
 * Never called from the foreground.
 */
class BackgroundSession @Inject constructor(
    private val auth: Auth,
    private val clock: Clock,
) {

    suspend fun prepare(): BackgroundSessionResult {
        when (auth.sessionStatus.value) {
            is SessionStatus.Authenticated -> return BackgroundSessionResult.READY
            is SessionStatus.NotAuthenticated -> return BackgroundSessionResult.NO_SESSION
            // The SDK is refreshing or retrying on its own; a second refresh must not race it.
            is SessionStatus.RefreshFailure -> return BackgroundSessionResult.RETRY_LATER
            SessionStatus.Initializing -> if (auth.isAutoRefreshRunning) return BackgroundSessionResult.RETRY_LATER
        }
        return try {
            if (!auth.loadFromStorage(autoRefresh = false)) return BackgroundSessionResult.NO_SESSION
            val stored = auth.currentSessionOrNull() ?: return BackgroundSessionResult.NO_SESSION
            val refreshBy = clock.instant().plus(EXPIRY_MARGIN).toKotlinInstant()
            if (stored.expiresAt > refreshBy) return BackgroundSessionResult.READY
            val refreshed = auth.refreshSession(stored.refreshToken)
            auth.importSession(refreshed, autoRefresh = false, source = SessionSource.Refresh(stored))
            BackgroundSessionResult.READY
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            // A rejected refresh token stays as it is: the SDK decides about it, as it does today,
            // once the app is back in the foreground. Only server failures are worth another try.
            if (e.statusCode >= 500) BackgroundSessionResult.RETRY_LATER else BackgroundSessionResult.REJECTED
        } catch (_: Exception) {
            // No usable answer from the network or the server.
            BackgroundSessionResult.RETRY_LATER
        }
    }

    private companion object {
        // Enough to finish a drain with the token the session has.
        val EXPIRY_MARGIN: Duration = Duration.ofMinutes(5)
    }
}

enum class BackgroundSessionResult {
    /** A current session with a usable access token. */
    READY,

    /** No session is stored: nobody is signed in. */
    NO_SESSION,

    /** The session could not be made usable now, but may be later. */
    RETRY_LATER,

    /** The backend refused the stored session's refresh token; trying again cannot help. */
    REJECTED,
}
