package app.gifty.domain.repository

import app.gifty.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** The active account's local sync status; reading it never syncs anything. */
interface SyncStatusRepository {

    /** Emits again whenever the local state behind it changes; with no account, nothing waits. */
    fun observeSyncStatus(): Flow<SyncStatus>

    /**
     * Records that the user has seen conflict [id]. It is kept, but no longer shown. Changes no
     * synced data and asks for no sync; dismissing it again, or a conflict that is not the
     * account's, changes nothing.
     */
    suspend fun dismissConflict(id: UUID)
}
