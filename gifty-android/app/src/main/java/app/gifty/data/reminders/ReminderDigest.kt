package app.gifty.data.reminders

import app.gifty.domain.model.NotificationAccess
import app.gifty.domain.model.UpcomingOccasion
import app.gifty.domain.repository.AccountSession
import app.gifty.domain.repository.OccasionRepository
import app.gifty.domain.repository.OccasionSnapshot
import app.gifty.domain.repository.OwnerSessionState
import app.gifty.domain.usecase.dueReminders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * One heartbeat of the daily reminder, as [ReminderWorker] runs it: posts the open account's
 * digest of occasions today and in a week, at most once a day, between 09:00 and 21:00 local time.
 *
 * Reminders only read this device's data, so the stored account opening is enough; no session is
 * refreshed. A day counts as reminded only once the system accepted a digest. Not allowed, nothing
 * due, or a failed read leave the day open for a later heartbeat.
 *
 * Not a singleton: each run takes the device's time zone as it is then.
 */
class ReminderDigest internal constructor(
    private val accountState: StateFlow<OwnerSessionState>,
    private val preferences: ReminderPreferences,
    private val clock: Clock,
    private val zone: ZoneId,
    private val access: () -> NotificationAccess,
    private val snapshot: suspend () -> OccasionSnapshot?,
    private val post: (owner: UUID, due: List<UpcomingOccasion>) -> Boolean,
    private val cancel: (owner: UUID) -> Unit,
) {

    @Inject
    constructor(
        accountSession: AccountSession,
        preferences: ReminderPreferences,
        clock: Clock,
        zone: ZoneId,
        access: DeviceNotificationAccess,
        repository: OccasionRepository,
        notifier: ReminderNotifier,
    ) : this(accountSession.state, preferences, clock, zone, access::read, repository::getOccasionSnapshot, notifier::post, notifier::cancel)

    /** [attempt] is how often this run was tried before. True if it should be tried again soon. */
    suspend fun run(attempt: Int): Boolean {
        val state = withTimeoutOrNull(OPEN_TIMEOUT) { accountState.first { it !is OwnerSessionState.Restoring } }
            ?: return retryUnlessExhausted(attempt)
        val owner = (state as? OwnerSessionState.Ready)?.user?.id ?: return false
        if (!preferences.isEnabled(owner)) return false

        val now = clock.instant().atZone(zone)
        // Never at night: a heartbeat outside the window just leaves it to a later one.
        if (now.toLocalTime().isBefore(WINDOW_START) || !now.toLocalTime().isBefore(WINDOW_END)) return false
        val today = now.toLocalDate()
        // After the zone moves west the date can step back; a day already reminded stays done.
        if (preferences.lastDigest(owner)?.let { it >= today } == true) return false
        if (access() != NotificationAccess.GRANTED) return false

        val occasions = try {
            snapshot()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return retryUnlessExhausted(attempt)
        }
        if (occasions == null || occasions.ownerId != owner) return false
        val due = dueReminders(occasions.entries, today)
        if (due.isEmpty()) return false

        if (!isOpen(owner)) return false
        val posted = post(owner, due)
        // The account may have closed while posting; its digest must not outlive it.
        if (!isOpen(owner)) {
            cancel(owner)
            return false
        }
        if (posted) preferences.setLastDigest(owner, today)
        return false
    }

    private fun isOpen(owner: UUID) = (accountState.value as? OwnerSessionState.Ready)?.user?.id == owner

    private fun retryUnlessExhausted(attempt: Int) = attempt < RETRY_LIMIT

    companion object {
        /** Once a run was tried this often, the next heartbeat takes over. */
        const val RETRY_LIMIT = 3

        private val OPEN_TIMEOUT = 60.seconds
        private val WINDOW_START: LocalTime = LocalTime.of(9, 0)
        private val WINDOW_END: LocalTime = LocalTime.of(21, 0)
    }
}
