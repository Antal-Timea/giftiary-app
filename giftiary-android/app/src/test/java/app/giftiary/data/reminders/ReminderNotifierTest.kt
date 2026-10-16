package app.giftiary.data.reminders

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.Configuration
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import app.giftiary.MainActivity
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.UpcomingOccasion
import app.giftiary.domain.model.turningAge
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ReminderNotifierTest {

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val manager = application.getSystemService(NotificationManager::class.java)
    private val alice = UUID.randomUUID()
    private val bob = UUID.randomUUID()
    private val anna = person("Anna")
    private val today = LocalDate.of(2026, 5, 10)
    private val due = listOf(
        upcoming(OccasionDetails(OccasionType.BIRTHDAY, null, 10, 5, 1990, true), daysUntil = 0),
        upcoming(OccasionDetails(OccasionType.CHRISTMAS, null, 17, 5, null, true), daysUntil = 7, withPerson = false),
    )

    @Before
    fun setUp() {
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test
    fun `a digest lists what is due, in the account's own slot, with names kept off the lock screen`() {
        assertTrue(notifier().post(alice, due))

        val notification = shadowOf(manager).getNotification(alice.toString(), ReminderNotifier.DIGEST_ID)
        assertNotNull(notification)
        assertEquals(ReminderNotifier.CHANNEL_ID, notification.channelId)
        assertEquals("Occasion reminders", manager.getNotificationChannel(ReminderNotifier.CHANNEL_ID).name)
        assertEquals("Coming up", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals(
            listOf("Anna: Birthday · turns 36, today", "Christmas, in 7 days"),
            notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() },
        )
        assertTrue("a same-day repost stays quiet", notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertEquals(NotificationCompat.VISIBILITY_PRIVATE, notification.visibility)
        val public = notification.publicVersion!!
        val publicText = "${public.extras.getCharSequence(Notification.EXTRA_TITLE)} ${public.extras.getCharSequence(Notification.EXTRA_TEXT)}"
        assertEquals("Giftiary You have occasions coming up.", publicText)
        assertFalse(publicText.contains("Anna") || publicText.contains("Birthday") || publicText.contains("Christmas"))
    }

    @Test
    fun `tapping the digest opens the app, nothing more specific`() {
        notifier().post(alice, due)

        val intent = shadowOf(shadowOf(manager).getNotification(alice.toString(), ReminderNotifier.DIGEST_ID).contentIntent)
        assertEquals(MainActivity::class.java.name, intent.savedIntent.component?.className)
        assertEquals(android.content.Intent.ACTION_MAIN, intent.savedIntent.action)
        assertTrue(intent.flags and PendingIntent.FLAG_IMMUTABLE != 0)
    }

    @Test
    fun `a second digest for an account replaces its first, another account's stays`() {
        notifier().post(alice, due)
        notifier().post(alice, due.take(1))
        notifier().post(bob, due)

        val tags = manager.activeNotifications.map { it.tag }
        assertEquals(listOf(alice.toString(), bob.toString()).sorted(), tags.sorted())
    }

    @Test
    fun `nothing is posted without permission, with notifications off, or with the channel off`() {
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(notifier().post(alice, due))
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        shadowOf(manager).setNotificationsEnabled(false)
        assertFalse(notifier().post(alice, due))
        shadowOf(manager).setNotificationsEnabled(true)

        manager.createNotificationChannel(NotificationChannel(ReminderNotifier.CHANNEL_ID, "Occasion reminders", NotificationManager.IMPORTANCE_NONE))
        assertFalse(notifier().post(alice, due))

        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test
    fun `clearing for an account removes only other accounts' digests, never other notifications`() {
        notifier().post(alice, due)
        notifier().post(bob, due)
        val other = NotificationCompat.Builder(application, "other").setSmallIcon(android.R.drawable.ic_dialog_info).build()
        manager.createNotificationChannel(NotificationChannel("other", "Other", NotificationManager.IMPORTANCE_DEFAULT))
        manager.notify(ReminderNotifier.DIGEST_ID, other)

        notifier().cancelAllExcept(bob)
        assertEquals(setOf(bob.toString(), null), manager.activeNotifications.map { it.tag }.toSet())

        notifier().cancelAllExcept(null)
        assertEquals(listOf<String?>(null), manager.activeNotifications.map { it.tag })
    }

    @Test
    fun `the digest reads naturally in Hungarian`() {
        val hungarian = application.createConfigurationContext(Configuration(application.resources.configuration).apply { setLocale(Locale.forLanguageTag("hu")) })

        notifier(hungarian).post(alice, due)

        val notification = shadowOf(manager).getNotification(alice.toString(), ReminderNotifier.DIGEST_ID)
        assertEquals(
            listOf("Anna: Születésnap · 36 éves lesz, ma", "Karácsony, 7 nap múlva"),
            notification.extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)!!.map { it.toString() },
        )
        assertEquals("Közelgő alkalmaid vannak.", notification.publicVersion!!.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    private fun notifier(context: Context = application) = ReminderNotifier(context, DeviceNotificationAccess(context))

    private fun upcoming(details: OccasionDetails, daysUntil: Int, withPerson: Boolean = true): UpcomingOccasion {
        val occasion = occasion(if (withPerson) anna.id else null, details)
        val date = today.plusDays(daysUntil.toLong())
        return UpcomingOccasion(occasion, anna.takeIf { withPerson }, date, daysUntil, turningAge(occasion, date))
    }
}
