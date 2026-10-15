package app.gifty.data.reminders

import javax.inject.Inject

/**
 * What an explicit sign-out does to reminders, at once rather than when the network sign-out
 * finishes: the heartbeat stops and the account's digest goes. Its choice and its reminded day stay
 * for when it signs in again.
 */
class ReminderSignOut @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
) {

    fun onSignOut() {
        scheduler.cancel()
        // Nobody is signed in afterwards, so no digest is anyone's to keep.
        notifier.cancelAllExcept(null)
    }
}
