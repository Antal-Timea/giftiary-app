package app.giftiary.presentation

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import app.giftiary.R
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

/** The words the redesigned Home, People and Person detail added exist in Hungarian too, not only in English. */
@RunWith(RobolectricTestRunner::class)
class RedesignStringsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun localized(language: String): Context =
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) })

    @Test
    fun `the new strings are translated`() {
        val english = localized("en")
        val hungarian = localized("hu")
        val strings = listOf(
            R.string.home_title, R.string.home_script, R.string.home_recent_gifts, R.string.home_recent_gifts_subtitle,
            R.string.home_see_all, R.string.home_recent_gifts_empty, R.string.home_date_pattern, R.string.coming_up_no_ideas,
            R.string.people_subtitle, R.string.people_search_hint, R.string.people_all, R.string.people_no_results_title,
            R.string.people_no_results_message, R.string.gift_given_to, R.string.gift_received_from,
            R.string.person_detail_history, R.string.person_detail_edit, R.string.person_detail_plan_gift,
            R.string.person_detail_gift_ideas_subtitle, R.string.person_detail_add_gift_idea,
        )
        strings.forEach { id ->
            assertNotEquals(english.resources.getResourceEntryName(id), english.getString(id), hungarian.getString(id))
        }
        listOf(R.plurals.coming_up_days_unit, R.plurals.people_no_results_count, R.plurals.person_detail_history_count).forEach { id ->
            assertNotEquals(
                english.resources.getResourceEntryName(id),
                english.resources.getQuantityString(id, 2, 2),
                hungarian.resources.getQuantityString(id, 2, 2),
            )
        }
    }
}
