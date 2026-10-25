package app.giftiary.data.reminders

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Reminder settings on this device, kept per account so that one account never inherits another's
 * choice or another's "already reminded today". Signing out keeps them for the account's return.
 */
class ReminderPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun isEnabled(owner: UUID): Boolean = preferences.getBoolean(enabledKey(owner), false)

    fun setEnabled(owner: UUID, enabled: Boolean) {
        preferences.edit { putBoolean(enabledKey(owner), enabled) }
    }

    fun lastDigest(owner: UUID): LocalDate? =
        preferences.getString(lastDigestKey(owner), null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Written to disk before returning: the next job must see it even if this process dies right after. */
    fun setLastDigest(owner: UUID, date: LocalDate) {
        preferences.edit(commit = true) { putString(lastDigestKey(owner), date.toString()) }
    }

    /** For an account that no longer exists: nothing of it is kept for a return. */
    fun forget(owner: UUID) {
        preferences.edit(commit = true) {
            remove(enabledKey(owner))
            remove(lastDigestKey(owner))
        }
    }

    private fun enabledKey(owner: UUID) = "enabled.$owner"

    private fun lastDigestKey(owner: UUID) = "lastDigest.$owner"

    private companion object {
        const val PREFERENCES_NAME = "giftiary_reminders"
    }
}
