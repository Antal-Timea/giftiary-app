package app.gifty.data.reminders

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.gifty.MainActivity
import app.gifty.R
import app.gifty.domain.model.NotificationAccess
import app.gifty.domain.model.OccasionType
import app.gifty.domain.model.UpcomingOccasion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * Shows an account's daily reminder digest. Each account has its own slot, tagged with its id, so
 * a new digest replaces that account's earlier one and never another account's.
 */
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: DeviceNotificationAccess,
) {

    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannel() {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminder_channel_name))
                .setDescription(context.getString(R.string.reminder_channel_description))
                .build(),
        )
    }

    /** True only if the system accepted the digest; false if notifications are not allowed now. */
    // The permission is checked through [access] right before posting, and a revocation in between
    // surfaces as the SecurityException caught below.
    @SuppressLint("MissingPermission")
    fun post(owner: UUID, due: List<UpcomingOccasion>): Boolean {
        if (access.read() != NotificationAccess.GRANTED) return false
        ensureChannel()
        val lines = due.map(::line)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_calendar)
            .setContentTitle(context.getString(R.string.coming_up_title))
            .setContentText(lines.joinToString("; "))
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.forEach(style::addLine) })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            // A same-day repost, e.g. after the process died before the day was recorded, stays quiet.
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion())
            .build()
        return try {
            manager.notify(owner.toString(), DIGEST_ID, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun cancel(owner: UUID) {
        manager.cancel(owner.toString(), DIGEST_ID)
    }

    /** Removes every reminder digest that is not [owner]'s; all of them if [owner] is null. Other notifications stay. */
    fun cancelAllExcept(owner: UUID?) {
        val keep = owner?.toString()
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.id == DIGEST_ID && it.notification.channelId == CHANNEL_ID && it.tag != keep }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    // Names and occasions stay off the lock screen.
    private fun publicVersion() = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_calendar)
        .setContentTitle(context.getString(R.string.app_name))
        .setContentText(context.getString(R.string.reminder_public_text))
        .build()

    // Like the launcher icon: Gifty opens on Home, or comes back as the user left it.
    private fun openApp(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun line(upcoming: UpcomingOccasion): String {
        val occasion = upcoming.occasion
        val title = occasion.title?.takeIf { occasion.type == OccasionType.CUSTOM } ?: context.getString(typeLabel(occasion.type))
        val name = upcoming.person?.name
        val details = listOfNotNull(
            title.takeIf { name != null },
            upcoming.turningAge?.let { context.getString(R.string.coming_up_turns, it) },
        ).joinToString(" · ")
        val days = upcoming.daysUntil
        val whenText = if (days == 0) {
            context.getString(R.string.reminder_today)
        } else {
            context.resources.getQuantityString(R.plurals.reminder_in_days, days, days)
        }
        return if (details.isEmpty()) {
            context.getString(R.string.reminder_line_short, name ?: title, whenText)
        } else {
            context.getString(R.string.reminder_line, name ?: title, details, whenText)
        }
    }

    @StringRes
    private fun typeLabel(type: OccasionType): Int = when (type) {
        OccasionType.BIRTHDAY -> R.string.occasion_type_birthday
        OccasionType.NAME_DAY -> R.string.occasion_type_name_day
        OccasionType.ANNIVERSARY -> R.string.occasion_type_anniversary
        OccasionType.CHRISTMAS -> R.string.occasion_type_christmas
        OccasionType.CUSTOM -> R.string.occasion_type_custom
    }

    companion object {
        const val CHANNEL_ID = "occasion_reminders"

        /** One digest slot per account; the account's id is the tag. */
        const val DIGEST_ID = 1
    }
}
