package app.giftiary.domain.repository

import app.giftiary.domain.model.NotificationAccess

/**
 * The current account's daily occasion reminders. The choice is kept on this device, for each
 * account separately, and survives signing out and in again.
 */
interface ReminderSettingsRepository {

    /** Null without an account. */
    fun isEnabled(): Boolean?

    /** Turning reminders on keeps the daily check scheduled; turning them off stops it. Ignored without an account. */
    fun setEnabled(enabled: Boolean)

    /** Read fresh every time: the user can change it in the system settings at any moment. */
    fun notificationAccess(): NotificationAccess
}
