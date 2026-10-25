package app.giftiary.data.user

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * The account whose deletion was sent to the server and has not been finished on this device.
 * Written before the request and cleared only once the device is clean (or the server refused), so
 * a process that dies in between still knows on its next start. Excluded from backups.
 */
class AccountDeletionMarker @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun owner(): UUID? = preferences.getString(OWNER, null)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    // Both are on disk before returning: a request may only leave once its marker is.
    fun set(owner: UUID) {
        preferences.edit(commit = true) { putString(OWNER, owner.toString()) }
    }

    fun clear() {
        preferences.edit(commit = true) { remove(OWNER) }
    }

    private companion object {
        const val PREFERENCES_NAME = "giftiary_account_deletion"
        const val OWNER = "owner"
    }
}
