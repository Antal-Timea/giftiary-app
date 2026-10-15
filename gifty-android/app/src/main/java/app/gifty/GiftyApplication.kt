package app.gifty

import android.app.Application
import app.gifty.data.reminders.ReminderAccountFollower
import app.gifty.data.sync.ForegroundSyncCoordinator
import app.gifty.data.user.OwnerSession
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GiftyApplication : Application() {

    @Inject
    lateinit var ownerSession: OwnerSession

    @Inject
    lateinit var syncCoordinator: ForegroundSyncCoordinator

    @Inject
    lateinit var reminderAccountFollower: ReminderAccountFollower

    override fun onCreate() {
        super.onCreate()
        // Follows the signed-in account for the whole process, independent of any screen.
        ownerSession.start()
        syncCoordinator.start()
        reminderAccountFollower.start()
    }
}
