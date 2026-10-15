package app.gifty.data.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.gifty.domain.model.NotificationAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Reads what the system currently allows; never cached, since the user can change it at any moment. */
class DeviceNotificationAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun read(): NotificationAccess {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationAccess.PERMISSION_MISSING
        }
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return NotificationAccess.BLOCKED
        val channel = manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID)
        if (channel?.importance == NotificationManager.IMPORTANCE_NONE) return NotificationAccess.BLOCKED
        return NotificationAccess.GRANTED
    }
}
