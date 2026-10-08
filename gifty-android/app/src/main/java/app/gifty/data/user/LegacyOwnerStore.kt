package app.gifty.data.user

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * The device-local id that owned all data before accounts existed, read only to hand that data
 * over to the first account (see [app.gifty.data.local.LegacyOwnershipClaim]).
 *
 * Never creates, replaces or guesses an id: a freshly made id would let the claim take unowned
 * sync operations away from the rows they belong to. A missing or malformed value means there is
 * no legacy data to claim. The preference itself is kept, also in backups.
 */
class LegacyOwnerStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun existingLegacyOwnerIdOrNull(): UUID? = withContext(Dispatchers.IO) {
        val stored = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getString(KEY_USER_ID, null)
        stored?.let(::parseStrictly)
    }

    // UUID.fromString also accepts shortened forms such as "1-2-3-4-5"; only the exact form the
    // app once wrote is trusted.
    private fun parseStrictly(value: String): UUID? =
        runCatching { UUID.fromString(value) }.getOrNull()?.takeIf { it.toString() == value.lowercase() }

    private companion object {
        // Where the app kept its device-local owner id before accounts existed; read-only now.
        const val PREFERENCES_NAME = "local_user"
        const val KEY_USER_ID = "user_id"
    }
}
