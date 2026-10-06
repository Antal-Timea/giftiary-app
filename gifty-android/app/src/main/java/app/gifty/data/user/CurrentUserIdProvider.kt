package app.gifty.data.user

import java.util.UUID

/**
 * The user who owns locally created rows. This is a device-local identity, deliberately separate
 * from the signed-in Supabase account ([app.gifty.domain.repository.AuthRepository]): the two ids
 * differ, and existing rows keep this owner until local ownership is migrated in a later phase.
 */
interface CurrentUserIdProvider {
    suspend fun currentUserId(): UUID
}
