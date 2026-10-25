package app.giftiary

import android.app.Application
import app.giftiary.data.reminders.ReminderAccountFollower
import app.giftiary.data.sync.ForegroundSyncCoordinator
import app.giftiary.data.user.AccountDeleter
import app.giftiary.data.user.OwnerSession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GiftiaryApplication : Application() {

    @Inject
    lateinit var ownerSession: OwnerSession

    @Inject
    lateinit var syncCoordinator: ForegroundSyncCoordinator

    @Inject
    lateinit var reminderAccountFollower: ReminderAccountFollower

    @Inject
    lateinit var accountDeleter: AccountDeleter

    override fun onCreate() {
        super.onCreate()
        // Follows the signed-in account for the whole process, independent of any screen.
        ownerSession.start()
        syncCoordinator.start()
        reminderAccountFollower.start()
        accountDeleter.start()
    }
}
