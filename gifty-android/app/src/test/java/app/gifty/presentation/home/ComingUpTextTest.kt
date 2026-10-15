package app.gifty.presentation.home

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.gifty.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ComingUpTextTest {

    @Test
    fun `how soon an occasion is reads naturally in both languages`() {
        val en = context(Locale.ENGLISH)
        assertEquals("In 1 day", en.resources.getQuantityString(R.plurals.coming_up_in_days, 1, 1))
        assertEquals("In 12 days", en.resources.getQuantityString(R.plurals.coming_up_in_days, 12, 12))
        assertEquals("turns 36", en.getString(R.string.coming_up_turns, 36))
        assertEquals("Nothing coming up in the next 60 days", en.resources.getQuantityString(R.plurals.coming_up_empty, 60, 60))

        val hu = context(Locale.forLanguageTag("hu"))
        assertEquals("Ma", hu.getString(R.string.coming_up_today))
        assertEquals("Holnap", hu.getString(R.string.coming_up_tomorrow))
        assertEquals("12 nap múlva", hu.resources.getQuantityString(R.plurals.coming_up_in_days, 12, 12))
        assertEquals("36 éves lesz", hu.getString(R.string.coming_up_turns, 36))
        assertEquals("A következő 60 napban nincs közelgő alkalom", hu.resources.getQuantityString(R.plurals.coming_up_empty, 60, 60))
    }

    @Test
    fun `the reminder card says what it offers in both languages`() {
        val en = context(Locale.ENGLISH)
        assertEquals("Get a reminder on the day of an occasion and a week before.", en.getString(R.string.reminder_card_off))
        assertEquals("Turn on", en.getString(R.string.reminder_turn_on))
        assertEquals("Notifications are turned off for Gifty.", en.getString(R.string.reminder_card_blocked))

        val hu = context(Locale.forLanguageTag("hu"))
        assertEquals("Kérj emlékeztetőt az alkalom napján és egy héttel előtte.", hu.getString(R.string.reminder_card_off))
        assertEquals("Engedélyezd az értesítéseket az emlékeztetőkhöz.", hu.getString(R.string.reminder_card_needs_permission))
        assertEquals("Beállítások megnyitása", hu.getString(R.string.reminder_open_settings))
    }

    @Test
    fun `the user's own occasions are named in both languages`() {
        val en = context(Locale.ENGLISH)
        assertEquals("Your occasions", en.getString(R.string.own_occasions_title))
        assertEquals("No occasions of your own yet", en.getString(R.string.own_occasions_empty_title))

        val hu = context(Locale.forLanguageTag("hu"))
        assertEquals("Saját alkalmaid", hu.getString(R.string.own_occasions_title))
        assertEquals("A saját alkalmaid most nem jeleníthetők meg.", hu.getString(R.string.own_occasions_load_failed))
    }

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }
}
