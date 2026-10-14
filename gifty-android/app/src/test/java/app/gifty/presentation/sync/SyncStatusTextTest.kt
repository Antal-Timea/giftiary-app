package app.gifty.presentation.sync

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.gifty.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/**
 * What the status texts promise, in both languages: only what this device knows. Nothing may
 * claim that the device matches the account, since no proof of that is kept.
 */
@RunWith(RobolectricTestRunner::class)
class SyncStatusTextTest {

    @Test
    fun `with nothing waiting the row says so, and nothing claims everything is synced`() {
        assertEquals("No changes waiting to sync", context(Locale.ENGLISH).getString(R.string.sync_status_nothing_waiting))
        assertEquals("Nincs szinkronizálásra váró változás", context(Locale.forLanguageTag("hu")).getString(R.string.sync_status_nothing_waiting))

        listOf(Locale.ENGLISH, Locale.forLanguageTag("hu")).forEach { locale ->
            val texts = statusTexts(context(locale))
            assertFalse("$locale: $texts", texts.any { it.contains("all synced", ignoreCase = true) || it.contains("szinkronizálva", ignoreCase = true) })
        }
    }

    @Test
    fun `counts read naturally in both languages`() {
        val en = context(Locale.ENGLISH).resources
        assertEquals("1 change saved on this device, waiting to sync", en.getQuantityString(R.plurals.sync_status_waiting, 1, 1))
        assertEquals("3 items need your attention", en.getQuantityString(R.plurals.sync_status_attention, 3, 3))
        val hu = context(Locale.forLanguageTag("hu")).resources
        assertEquals("3 változás elmentve ezen az eszközön, szinkronizálásra vár", hu.getQuantityString(R.plurals.sync_status_waiting, 3, 3))
        assertEquals("1 elem figyelmet igényel", hu.getQuantityString(R.plurals.sync_status_attention, 1, 1))
    }

    private fun statusTexts(context: Context): List<String> = listOf(
        R.string.sync_status_nothing_waiting,
        R.string.sync_status_unavailable,
        R.string.sync_attention_title,
        R.string.sync_attention_empty_title,
        R.string.sync_attention_empty_message,
    ).map(context::getString) + listOf(R.plurals.sync_status_waiting, R.plurals.sync_status_attention).flatMap { plural ->
        listOf(1, 3).map { context.resources.getQuantityString(plural, it, it) }
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }
}
