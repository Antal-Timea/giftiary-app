package app.gifty.data.user

import android.annotation.SuppressLint
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a user id once and keeps it for the lifetime of the installation, so every row
 * created before sign-in has the same owner.
 */
@Singleton
class LocalUserIdProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : CurrentUserIdProvider {

    private val mutex = Mutex()

    @Volatile
    private var cachedId: UUID? = null

    override suspend fun currentUserId(): UUID = cachedId ?: mutex.withLock {
        cachedId ?: withContext(Dispatchers.IO) { loadOrCreate() }.also { cachedId = it }
    }

    // The KTX edit {} helper discards commit()'s result, which is checked below.
    @SuppressLint("UseKtx")
    private fun loadOrCreate(): UUID {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        preferences.getString(KEY_USER_ID, null)?.let { return UUID.fromString(it) }

        val id = UUID.randomUUID()
        // commit() rather than apply(): rows referencing this id may be written right after.
        check(preferences.edit().putString(KEY_USER_ID, id.toString()).commit()) {
            "Could not persist the local user id"
        }
        return id
    }

    private companion object {
        const val PREFERENCES_NAME = "local_user"
        const val KEY_USER_ID = "user_id"
    }
}
