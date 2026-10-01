package app.gifty.data.user

import java.util.UUID

/**
 * The user who owns locally created rows. Until authentication exists this is a device-local
 * identity; it will be replaced by the signed-in Supabase user's id.
 */
interface CurrentUserIdProvider {
    suspend fun currentUserId(): UUID
}
