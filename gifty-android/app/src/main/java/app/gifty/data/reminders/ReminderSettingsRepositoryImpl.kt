package app.gifty.data.reminders

import app.gifty.data.user.ActiveOwner
import app.gifty.domain.model.NotificationAccess
import app.gifty.domain.repository.ReminderSettingsRepository
import javax.inject.Inject

class ReminderSettingsRepositoryImpl @Inject constructor(
    private val activeOwner: ActiveOwner,
    private val preferences: ReminderPreferences,
    private val scheduler: ReminderScheduler,
    private val notifier: ReminderNotifier,
    private val access: DeviceNotificationAccess,
) : ReminderSettingsRepository {

    override fun isEnabled(): Boolean? = activeOwner.ownerId.value?.let(preferences::isEnabled)

    override fun setEnabled(enabled: Boolean) {
        val owner = activeOwner.ownerId.value ?: return
        preferences.setEnabled(owner, enabled)
        if (enabled) {
            // The channel exists from the start, so the system settings can show it even before a digest.
            notifier.ensureChannel()
            scheduler.ensureScheduled()
        } else {
            scheduler.cancel()
            notifier.cancel(owner)
        }
    }

    override fun notificationAccess(): NotificationAccess = access.read()
}
