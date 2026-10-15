package app.gifty.data.reminders

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import app.gifty.domain.model.NotificationAccess
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DeviceNotificationAccessTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val access = DeviceNotificationAccess(application)

    @Test
    @Config(sdk = [33])
    fun `from Android 13 the permission comes first, then the app's and the channel's switches`() {
        assertEquals(NotificationAccess.PERMISSION_MISSING, access.read())

        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(NotificationAccess.GRANTED, access.read())

        manager.createNotificationChannel(NotificationChannel(ReminderNotifier.CHANNEL_ID, "Occasion reminders", NotificationManager.IMPORTANCE_NONE))
        assertEquals(NotificationAccess.BLOCKED, access.read())
    }

    @Test
    @Config(sdk = [32])
    fun `before Android 13 there is no permission to ask for, only switches`() {
        assertEquals(NotificationAccess.GRANTED, access.read())

        shadowOf(manager).setNotificationsEnabled(false)
        assertEquals(NotificationAccess.BLOCKED, access.read())
    }
}
