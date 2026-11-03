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

/** The words the redesigned Home, People, Person detail, Gifts, Lists and the task flows added exist in Hungarian too, not only in English. */
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
            R.string.gifts_history_title, R.string.gifts_subtitle, R.string.gifts_search_hint,
            R.string.gifts_no_results_title, R.string.gifts_no_results_message, R.string.gift_detail_more,
            R.string.lists_title, R.string.lists_subtitle, R.string.lists_section_yours, R.string.lists_only_you,
            R.string.lists_shared_subtitle, R.string.lists_join_description, R.string.wishlist_more, R.string.wishlist_edit,
            R.string.wishlist_sharing,
            R.string.wish_add_description, R.string.wish_price_estimate, R.string.wish_view_link, R.string.wish_more,
            R.string.wish_edit, R.string.shared_wish_get, R.string.shared_wish_get_description, R.string.shared_wish_getting,
            R.string.shared_wish_undo, R.string.shared_wish_undo_description,
            R.string.person_editor_add_subtitle, R.string.person_editor_edit_subtitle, R.string.gift_editor_add_subtitle,
            R.string.gift_editor_edit_subtitle, R.string.gift_editor_step, R.string.gift_editor_step_direction,
            R.string.gift_editor_i_gave, R.string.gift_editor_i_received, R.string.gift_editor_step_person_given,
            R.string.gift_editor_step_person_received, R.string.gift_editor_search_people, R.string.gift_editor_no_match,
            R.string.gift_editor_new_person, R.string.gift_editor_new_person_description, R.string.gift_editor_step_name,
            R.string.gift_editor_name_placeholder, R.string.gift_editor_name_examples, R.string.gift_editor_more_details,
            R.string.gift_editor_fewer_details, R.string.gift_price, R.string.gift_note_placeholder,
            R.string.gift_product_url_placeholder, R.string.gift_idea_editor_add_subtitle, R.string.gift_idea_editor_edit_subtitle,
            R.string.gift_idea_for, R.string.gift_idea_occasion_optional, R.string.gift_idea_status,
            R.string.gift_idea_note_placeholder,
        )
        strings.forEach { id ->
            assertNotEquals(english.resources.getResourceEntryName(id), english.getString(id), hungarian.getString(id))
        }
        listOf(R.plurals.coming_up_days_unit, R.plurals.people_no_results_count, R.plurals.person_detail_history_count, R.plurals.gifts_no_results_count).forEach { id ->
            assertNotEquals(
                english.resources.getResourceEntryName(id),
                english.resources.getQuantityString(id, 2, 2),
                hungarian.resources.getQuantityString(id, 2, 2),
            )
        }
    }
}
