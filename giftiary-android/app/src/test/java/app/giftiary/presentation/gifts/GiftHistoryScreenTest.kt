package app.giftiary.presentation.gifts

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.WithFontScale
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.lineCount
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import org.robolectric.annotation.GraphicsMode

/** The phone Gift history: header, search, direction filters, the year and month groups, and its states. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class GiftHistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter")
    private val martin = person("Martin")
    private val watch = giftEntry(giftDetails(GiftDirection.RECEIVED, peter, "Garmin Watch", LocalDate.of(2026, 9, 12)), peter)
        .let { it.copy(occasion = occasion(peter.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 9, null, true))) }
    private val lego = giftEntry(giftDetails(GiftDirection.GIVEN, martin, "LEGO", LocalDate.of(2026, 6, 3)), martin)
        .let { it.copy(gift = it.gift.copy(price = Money(24990, "HUF"), photoPath = "lego.jpg")) }
    private val cookbook = giftEntry(giftDetails(GiftDirection.GIVEN, peter, "Cookbook", LocalDate.of(2025, 12, 24)), peter)
    private val actions = mutableListOf<String>()
    private var state by mutableStateOf(GiftHistoryUiState())
    private var showAddButton by mutableStateOf(false)
    private var shown = false

    private var fontScale = 1f

    private fun show(state: GiftHistoryUiState, showAddButton: Boolean = false) {
        this.state = state
        this.showAddButton = showAddButton
        if (shown) return
        shown = true
        compose.setContent {
            WithFontScale(fontScale) {
                GiftiaryTheme {
                    GiftHistoryScreen(
                        state = this.state,
                        onFilterChange = { actions += "filter $it" },
                        onQueryChange = { actions += "query '$it'" },
                        onAddGift = { actions += "add" },
                        onOpenGift = { actions += "open ${it.gift.name}" },
                        showAddButton = this.showAddButton,
                    )
                }
            }
        }
    }

    private fun loaded(vararg gifts: GiftEntry, photos: Map<String, LoadedPhoto> = emptyMap()) =
        GiftHistoryUiState(content = GiftHistoryContent.Loaded(groupByYearAndMonth(gifts.toList())), photos = photos)

    private fun top(text: String) = compose.onNodeWithText(text).getBoundsInRoot().top

    @Test
    fun `the history reads title, search, filters, then years and months, newest first`() {
        show(loaded(cookbook, lego, watch))

        val order = listOf(
            "Gift history", "Search, filter and revisit every shared gift.", "Search your gift history…", "All",
            "2026", "SEPTEMBER", "Garmin Watch", "JUNE", "LEGO", "2025", "DECEMBER", "Cookbook",
        )
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        assertEquals("the filters share a line", top("All"), top("Received"))
        compose.onNodeWithText("Ideas").assertDoesNotExist()
    }

    @Test
    fun `each row states its direction in words, its occasion and its price when known`() {
        show(loaded(watch, lego, cookbook))

        compose.onNodeWithText("Received from Peter · Birthday").assertExists()
        compose.onNodeWithText("Given to Martin").assertExists()
        compose.onNodeWithText("Given to Peter").assertExists()
        compose.onNodeWithText("24,990", substring = true).assertExists()
        compose.onNodeWithText("Cookbook").performClick()
        assertEquals(listOf("open Cookbook"), actions)
    }

    @Test
    fun `a gift with a photo and one without both render, the second with the fallback`() {
        val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
        val jpeg = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        show(loaded(lego, cookbook, photos = mapOf("lego.jpg" to LoadedPhoto(jpeg))))

        // Thumbnails are decorative: the name beside them says what each gift is.
        compose.onNodeWithText("LEGO").assertExists()
        compose.onNodeWithText("Cookbook").assertExists()
    }

    @Test
    fun `the filters show which is selected and report the choice`() {
        show(loaded(watch).copy(filter = GiftFilter.GIVEN))

        compose.onNodeWithText("Given").assertIsSelected()
        compose.onNodeWithText("All").assertIsNotSelected()
        compose.onNodeWithText("Received").performClick()
        compose.onNodeWithText("All").performClick()

        assertEquals(listOf("filter RECEIVED", "filter ALL"), actions)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    // Real text measurement, so a squeezed or clipped word shows.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian at large text the filters wrap, each label whole and still selectable`() {
        fontScale = 2f
        show(loaded(watch).copy(filter = GiftFilter.RECEIVED))

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Összes", "Adott", "Kapott").forEach {
            assertEquals(it, 1, compose.onNodeWithText(it, useUnmergedTree = true).lineCount())
            assertTrue("$it inside the screen", compose.onNodeWithText(it).getBoundsInRoot().right <= width)
        }
        compose.onNodeWithText("Kapott").assertIsSelected()
        compose.onNodeWithText("Adott").performClick()
        assertEquals(listOf("filter GIVEN"), actions)
    }

    @Test
    fun `phones add through the global action, so the history has no add button of its own`() {
        show(loaded(watch))
        compose.onNodeWithText("Add gift").assertDoesNotExist()

        show(loaded(watch), showAddButton = true)
        compose.onNodeWithText("Add gift").performClick()
        assertEquals(listOf("add"), actions)
    }

    @Test
    fun `no gifts at all invites the first one in place`() {
        show(GiftHistoryUiState(content = GiftHistoryContent.NoGifts))

        compose.onNodeWithText("Gift history").assertExists()
        compose.onNodeWithText("No gifts yet").assertExists()
        compose.onNodeWithText("Search your gift history…").assertDoesNotExist()
        compose.onNodeWithText("Add gift").performClick()
        assertEquals(listOf("add"), actions)
    }

    @Test
    fun `a search without matches says so for that search and clears it in place`() {
        show(GiftHistoryUiState(query = "socks ", content = GiftHistoryContent.NoMatches(total = 3)))

        compose.onNodeWithText("No gifts found for “socks”").assertExists()
        compose.onNodeWithText("3 gifts are still in your history.").assertExists()
        compose.onNodeWithText("Clear search").performClick()
        assertEquals(listOf("query ''"), actions)
    }

    @Test
    fun `a filter without matches says there are none that way yet`() {
        show(GiftHistoryUiState(filter = GiftFilter.RECEIVED, content = GiftHistoryContent.NoMatches(total = 1)))

        compose.onNodeWithText("No received gifts yet").assertExists()
        compose.onNodeWithText("Clear search").assertDoesNotExist()
        compose.onNodeWithText("Received").assertIsSelected()
    }

    @Test
    fun `only an unreadable history is a full message`() {
        show(GiftHistoryUiState(content = GiftHistoryContent.LoadFailed))

        compose.onNodeWithText("Gifts could not be loaded.").assertExists()
        compose.onNodeWithText("Gift history").assertDoesNotExist()
    }
}
