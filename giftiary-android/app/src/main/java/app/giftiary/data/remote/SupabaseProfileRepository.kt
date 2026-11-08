package app.giftiary.data.remote

import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.user.ActiveOwner
import app.giftiary.domain.repository.GiftPhotoUnavailableException
import app.giftiary.domain.repository.NoActiveOwnerException
import app.giftiary.domain.repository.OwnProfile
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.domain.repository.ProfileRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * [ProfileRepository] over the `profiles` table and the `profile-avatars` bucket, which the account
 * may use only for itself (row level security and Storage policies). Each call is made as the
 * account signed in when it starts.
 */
class SupabaseProfileRepository @Inject constructor(
    private val clients: AccountBoundClients,
    private val accounts: RemoteAccounts,
    private val activeOwner: ActiveOwner,
    private val avatars: ProfileAvatarRemote,
    private val files: GiftPhotoFiles,
    private val clock: Clock,
) : ProfileRepository {

    override suspend fun displayName(): String? = call { account ->
        select(account, Columns.list(DISPLAY_NAME)).displayName
    }

    override suspend fun setDisplayName(name: String?) {
        call { account -> update(account, buildJsonObject { put(DISPLAY_NAME, name) }) }
    }

    override suspend fun profile(): OwnProfile = call { account ->
        val row = select(account, Columns.list(DISPLAY_NAME, AVATAR_PATH))
        OwnProfile(row.displayName, row.avatarPath)
    }

    override suspend fun avatar(path: String): ByteArray = call { account ->
        avatars.download(account, own(account, path))
    }

    // The new object is stored before the row names it, and the old one is removed only after:
    // whatever fails, the row never names a missing object. An object left behind by a failure
    // is removed by a later successful change.
    override suspend fun setAvatar(photo: String): String = call { account ->
        own(account, photo)
        val bytes = withContext(Dispatchers.IO) { files.read(photo) } ?: throw GiftPhotoUnavailableException()
        val old = select(account, Columns.list(AVATAR_PATH)).avatarPath
        avatars.upload(account, photo, bytes)
        update(account, buildJsonObject { put(AVATAR_PATH, photo) })
        // Only now: until the row named it, the picture had to stay here for a retry.
        withContext(Dispatchers.IO) { files.delete(photo) }
        tidy(account, keep = photo, old = old)
        photo
    }

    override suspend fun removeAvatar() {
        call { account ->
            val old = select(account, Columns.list(AVATAR_PATH)).avatarPath
            update(account, buildJsonObject { put(AVATAR_PATH, null as String?) })
            tidy(account, keep = null, old = old)
        }
    }

    /**
     * Removes [old] and any older leftover besides [keep]. Never fails: the change is already
     * saved, and what stays behind is tried again next time. A leftover must be [LEFTOVER_AGE]
     * old, because one just uploaded may be another device's change about to be saved.
     */
    private suspend fun tidy(account: RemoteAccount, keep: String?, old: String?) {
        try {
            if (old != null && old != keep && GiftPhotoFiles.ownerOf(old) == account.ownerUserId) {
                avatars.delete(account, listOf(old))
            }
            val cutoff = clock.millis() - LEFTOVER_AGE.toMillis()
            val leftovers = avatars.list(account, LIST_LIMIT)
                .filter { it.path != keep && it.path != old && (it.createdAtMillis ?: Long.MAX_VALUE) < cutoff }
                .map { it.path }
            avatars.delete(account, leftovers)
        } catch (e: CancellationException) {
            throw e
        } catch (_: RemoteException) {
        }
    }

    /** [path] if it is shaped like an avatar of [account]; nothing is sent for any other. */
    private fun own(account: RemoteAccount, path: String): String {
        if (GiftPhotoFiles.ownerOf(path) != account.ownerUserId) throw ProfileException(ProfileFailure.SERVER)
        return path
    }

    private suspend fun select(account: RemoteAccount, columns: Columns): RemoteProfile =
        clients.call(account) { client ->
            client.from(TABLE).select(columns) {
                filter { eq("id", account.ownerUserId) }
                // Two, so that a second row would be seen and refused rather than cut off.
                limit(2)
            }.decodeList<RemoteProfile>()
        }.only()

    private suspend fun update(account: RemoteAccount, values: JsonObject) {
        clients.call(account) { client ->
            client.from(TABLE).update(values) {
                filter { eq("id", account.ownerUserId) }
                select(Columns.list(values.keys.toList()))
            }.decodeList<RemoteProfile>()
        }.only()
    }

    private suspend fun <T> call(block: suspend (RemoteAccount) -> T): T = try {
        block(accounts.capture(activeOwner.requireOwnerId()))
    } catch (e: CancellationException) {
        throw e
    } catch (e: ProfileException) {
        throw e
    } catch (e: RemoteException) {
        throw ProfileException(if (e.failure == RemoteFailure.NETWORK) ProfileFailure.NETWORK else ProfileFailure.SERVER)
    } catch (_: NoActiveOwnerException) {
        throw ProfileException(ProfileFailure.SERVER)
    }

    // Row level security shows the account its own row only, so no row means it has none.
    private fun List<RemoteProfile>.only(): RemoteProfile = when (size) {
        0 -> throw ProfileException(ProfileFailure.UNAVAILABLE)
        1 -> single()
        else -> throw ProfileException(ProfileFailure.SERVER)
    }

    @Serializable
    private data class RemoteProfile(
        @SerialName(DISPLAY_NAME) val displayName: String? = null,
        @SerialName(AVATAR_PATH) val avatarPath: String? = null,
    )

    private companion object {
        const val TABLE = "profiles"
        const val DISPLAY_NAME = "display_name"
        const val AVATAR_PATH = "avatar_path"
        val LEFTOVER_AGE: Duration = Duration.ofDays(1)
        const val LIST_LIMIT = 100
    }
}
