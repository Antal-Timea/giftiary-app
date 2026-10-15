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

    private fun context(locale: Locale): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }
}
