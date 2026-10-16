package app.giftiary.testing

import app.giftiary.domain.model.NotificationAccess
import app.giftiary.domain.repository.ReminderSettingsRepository
import java.util.UUID

/** Per-account choices like the real one; [access] stands for whatever the system currently allows. */
class FakeReminderSettingsRepository(var owner: UUID? = UUID.randomUUID()) : ReminderSettingsRepository {

    val enabled = mutableMapOf<UUID, Boolean>()
    var access = NotificationAccess.GRANTED

    override fun isEnabled(): Boolean? = owner?.let { enabled[it] ?: false }

    override fun setEnabled(enabled: Boolean) {
        owner?.let { this.enabled[it] = enabled }
    }

    override fun notificationAccess(): NotificationAccess = access
}
