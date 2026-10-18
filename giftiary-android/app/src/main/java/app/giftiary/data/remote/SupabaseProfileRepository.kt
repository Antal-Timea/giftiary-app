package app.giftiary.data.remote

import app.giftiary.data.user.ActiveOwner
import app.giftiary.domain.repository.NoActiveOwnerException
import app.giftiary.domain.repository.ProfileException
import app.giftiary.domain.repository.ProfileFailure
import app.giftiary.domain.repository.ProfileRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * [ProfileRepository] over the `profiles` table, which the account may read and update only for
 * itself (row level security). Each call is made as the account signed in when it starts.
 */
class SupabaseProfileRepository @Inject constructor(
    private val clients: AccountBoundClients,
    private val accounts: RemoteAccounts,
    private val activeOwner: ActiveOwner,
) : ProfileRepository {

    override suspend fun displayName(): String? = call { account ->
        clients.call(account) { client ->
            client.from(TABLE).select(Columns.list(DISPLAY_NAME)) {
                filter { eq("id", account.ownerUserId) }
                // Two, so that a second row would be seen and refused rather than cut off.
                limit(2)
            }.decodeList<RemoteProfile>()
        }.only().displayName
    }

    override suspend fun setDisplayName(name: String?) {
        call { account ->
            clients.call(account) { client ->
                client.from(TABLE).update(buildJsonObject { put(DISPLAY_NAME, name) }) {
                    filter { eq("id", account.ownerUserId) }
                    select(Columns.list(DISPLAY_NAME))
                }.decodeList<RemoteProfile>()
            }.only()
        }
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
    private data class RemoteProfile(@SerialName(DISPLAY_NAME) val displayName: String?)

    private companion object {
        const val TABLE = "profiles"
        const val DISPLAY_NAME = "display_name"
    }
}
