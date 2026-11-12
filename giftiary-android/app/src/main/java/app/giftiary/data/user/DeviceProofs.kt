package app.giftiary.data.user

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this device keeps to learn, later and without a session, whether an account it holds data
 * for was deleted elsewhere (see [DeletedAccountCheck]):
 *  - each account's device secret, saved only once the server has registered its hash, so a saved
 *    secret is always one the server knew;
 *  - the account whose data was open last, so a session that ended while the app was not running
 *    is still noticed;
 *  - the accounts whose data stays on this device without their session, still to be checked.
 *
 * Every write is on disk before it returns. Excluded from backups: a secret restored on another
 * device would let it ask about an account whose data it does not have.
 */
@Singleton
class DeviceProofs @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun secret(owner: UUID): String? = preferences.getString(SECRET_PREFIX + owner, null)

    fun saveSecret(owner: UUID, secret: String) {
        preferences.edit(commit = true) { putString(SECRET_PREFIX + owner, secret) }
    }

    fun lastOwner(): UUID? = preferences.getString(LAST_OWNER, null)?.let(::uuidOrNull)

    fun setLastOwner(owner: UUID) {
        preferences.edit(commit = true) { putString(LAST_OWNER, owner.toString()) }
    }

    fun unverified(): Set<UUID> = preferences.getStringSet(UNVERIFIED, emptySet()).orEmpty().mapNotNull(::uuidOrNull).toSet()

    fun markUnverified(owner: UUID) = updateUnverified { it + owner.toString() }

    fun markVerified(owner: UUID) = updateUnverified { it - owner.toString() }

    /** Everything about [owner], once its data is gone from this device. */
    fun forget(owner: UUID) {
        preferences.edit(commit = true) {
            remove(SECRET_PREFIX + owner)
            putStringSet(UNVERIFIED, preferences.getStringSet(UNVERIFIED, emptySet()).orEmpty() - owner.toString())
            if (lastOwner() == owner) remove(LAST_OWNER)
        }
    }

    // A copy: the set SharedPreferences returns must not be changed in place.
    private fun updateUnverified(change: (Set<String>) -> Set<String>) {
        val current = preferences.getStringSet(UNVERIFIED, emptySet()).orEmpty().toSet()
        val next = change(current)
        if (next != current) preferences.edit(commit = true) { putStringSet(UNVERIFIED, next) }
    }

    private fun uuidOrNull(value: String): UUID? = runCatching { UUID.fromString(value) }.getOrNull()

    companion object {
        private const val PREFERENCES_NAME = "giftiary_device_proofs"
        private const val SECRET_PREFIX = "secret."
        private const val LAST_OWNER = "last_owner"
        private const val UNVERIFIED = "unverified"

        private val random = SecureRandom()

        /** 32 random bytes as lower-case hex, the only shape the server accepts. */
        fun newSecret(): String = ByteArray(32).also(random::nextBytes).toHex()

        /** What the server stores: the secret's SHA-256 as lower-case hex. */
        fun hashOf(secret: String): String =
            MessageDigest.getInstance("SHA-256").digest(secret.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).toHex()

        private const val HEX = "0123456789abcdef"

        // By hand: formatting with "%02x" would follow the device's locale.
        private fun ByteArray.toHex() = buildString(size * 2) {
            for (byte in this@toHex) {
                append(HEX[(byte.toInt() shr 4) and 0xF])
                append(HEX[byte.toInt() and 0xF])
            }
        }
    }
}
