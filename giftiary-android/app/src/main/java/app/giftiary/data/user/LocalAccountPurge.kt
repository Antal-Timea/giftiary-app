package app.giftiary.data.user

import app.giftiary.data.local.dao.AccountDataDao
import app.giftiary.data.photos.GiftPhotoFiles
import app.giftiary.data.reminders.ReminderNotifier
import app.giftiary.data.reminders.ReminderPreferences
import app.giftiary.data.reminders.ReminderSignOut
import app.giftiary.data.sync.SyncScheduler
import app.giftiary.domain.model.AuthState
import app.giftiary.domain.repository.AuthRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject

/**
 * Removes a deleted account from this device: its sync and reminders stop, its reminder choices,
 * rows, photo files and device proof are deleted, and its session is forgotten without the network.
 *
 * Only for an account the server no longer has, which the caller must already know: the account's
 * own deletion ([AccountDeleter]) or the server's answer about it ([DeletedAccountCheck]). Every step
 * can run again, so a purge cut short is simply repeated. Rows and files of other accounts stay, and
 * so do the session, sync and reminders of another account signed in meanwhile.
 */
class LocalAccountPurge @Inject constructor(
    private val authRepository: AuthRepository,
    private val accountData: AccountDataDao,
    private val syncScheduler: SyncScheduler,
    private val reminderSignOut: ReminderSignOut,
    private val reminderPreferences: ReminderPreferences,
    private val reminderNotifier: ReminderNotifier,
    private val photoFiles: GiftPhotoFiles,
    private val deviceProofs: DeviceProofs,
) {

    /** @throws Exception if a step failed; the steps before it stay done. */
    suspend fun purge(owner: UUID) {
        val auth = authRepository.authState.first { it != AuthState.Restoring }
        val otherAccount = auth is AuthState.SignedIn && auth.user.id != owner
        if (otherAccount) {
            reminderNotifier.cancel(owner)
        } else {
            syncScheduler.cancelAll()
            reminderSignOut.onSignOut()
        }
        reminderPreferences.forget(owner)
        accountData.purge(owner)
        photoFiles.deleteAll(owner)
        deviceProofs.forget(owner)
        if (!otherAccount) authRepository.clearLocalSession()
    }
}
