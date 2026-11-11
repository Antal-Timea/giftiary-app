package app.giftiary.presentation.people

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.gifts.ShownPhotos
import app.giftiary.presentation.theme.GiftiarySpacing
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.WithFontScale
import app.giftiary.testing.assertNear
import app.giftiary.testing.awaitPhoto
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.lineCount
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The phone Person detail: its no-photo hero, the next occasion, its sections and what each action opens. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
class PersonDetailScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter", relationship = "Husband")
    private val today = LocalDate.of(2026, 9, 20)
    private val birthday = occasion(peter.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 10, 1985, true))
    private val nameDay = occasion(peter.id, OccasionDetails(OccasionType.NAME_DAY, null, 29, 6, null, true))
    private val christmasList = PersonWishlist(Wishlist(UUID.randomUUID(), peter.id, "Christmas", Instant.EPOCH, Instant.EPOCH), 2)
    private val actions = mutableListOf<String>()
    private var deletion by mutableStateOf(PersonDeletion())
    private var fontScale by mutableFloatStateOf(1f)

    private fun showDetail(
        state: PersonDetailUiState.Loaded,
        fontScale: Float = 1f,
        today: LocalDate = this.today,
        photo: LoadedPhoto? = null,
        giftPhotos: ShownPhotos = ShownPhotos.of(emptyMap()),
    ) {
        this.fontScale = fontScale
        compose.setContent {
            WithFontScale(this.fontScale) {
                GiftiaryTheme {
                    PersonDetailScreen(
                        state = state,
                        onBack = { actions += "back" },
                        onEditPerson = { actions += "edit ${it.name}" },
                        onAddOccasion = { actions += "add occasion" },
                        onEditOccasion = { actions += "edit occasion ${it.type}" },
                        onPlanGift = { actions += "plan ${it.type}" },
                        onAddGift = { actions += "add gift" },
                        onOpenGift = { actions += "gift ${it.gift.name}" },
                        onAddGiftIdea = { actions += "add idea" },
                        onOpenGiftIdea = { actions += "idea ${it.idea.name}" },
                        onAddWishlist = { actions += "add wishlist" },
                        onOpenWishlist = { actions += "wishlist" },
                        onDeleteRequested = { deletion = PersonDeletion(confirming = true) },
                        onDeleteConfirmed = { actions += "deleted" },
                        onDeleteDismissed = { deletion = PersonDeletion() },
                        onDeleteErrorShown = {},
                        deletion = deletion,
                        today = today,
                        personPhoto = photo,
                        giftPhotos = giftPhotos,
                    )
                }
            }
        }
    }

    @Test
    fun `a long history reads the photos of the cards on screen only, newest first, each once`() {
        // One gift a month back from today's month, each with its own photo.
        val gifts = (0 until 120).map { month ->
            giftEntry(giftDetails(person = peter, name = "Gift $month", givenAt = today.minusMonths(month.toLong())), peter)
                .let { it.copy(gift = it.gift.copy(photoPath = "photo-$month.jpg")) }
        }
        val reads = mutableListOf<String>()
        val bytes = jpeg().bytes
        val photos = ShownPhotos(CoroutineScope(Dispatchers.Unconfined), { path -> reads += path; bytes })

        showDetail(loaded(gifts = gifts), giftPhotos = photos)

        assertTrue("some cards are on screen", reads.isNotEmpty())
        assertTrue("a screenful of 120, not all: ${reads.size}", reads.size < 20)
        assertEquals(reads.distinct(), reads)
        assertEquals(gifts.take(reads.size).map { it.gift.photoPath }, reads)
    }

    private fun top(text: String) = compose.onAllNodesWithText(text, useUnmergedTree = true)[0].getBoundsInRoot().top

    private fun loaded(
        occasions: List<Occasion> = listOf(birthday, nameDay),
        ideas: List<GiftIdeaEntry> = emptyList(),
        gifts: List<GiftEntry> = emptyList(),
        wishlists: List<PersonWishlist> = emptyList(),
    ) = PersonDetailUiState.Loaded(peter, occasions, ideas, gifts, wishlists)

    @Test
    fun `without a photo the hero carries the monogram, the name and relationship, with back and edit`() {
        showDetail(loaded())

        // The monogram is decorative (see FoundationComponentsTest); the name is what is read out.
        compose.onNodeWithText("Peter").assertExists()
        compose.onNodeWithText("Husband").assertExists()
        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit Peter").performClick()
        assertEquals(listOf("back", "edit Peter"), actions)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `with a photo it fills the hero's circle, cropped and unannounced, beside the name`() {
        showDetail(loaded(), photo = jpeg())

        compose.awaitPhoto(PERSON_PHOTO_TAG)
        val photo = compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
            .getBoundsInRoot()
        // The circle runs off the hero's end, so only its height is whole: a 4:3 photo fitted
        // inside rather than cropped would be 147dp tall.
        assertEquals("fills the 196dp circle", 196f, (photo.bottom - photo.top).value, 0.5f)
        // Cropped, the photo's colour reaches the top of the circle; fitted, the circle's own fill
        // would show above and below it.
        val pixels = compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        val nearTop = pixels[pixels.width / 2, pixels.height / 20]
        assertEquals("photo colour near the top", 0x5A / 255f, nearTop.red, 0.06f)
        assertEquals(0x40 / 255f, nearTop.green, 0.06f)
        compose.onNodeWithText("Peter").assertExists()
        compose.onNodeWithContentDescription("Edit Peter").assertExists()
    }

    private fun jpeg(): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF5A4032.toInt()) }
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }

    @Test
    // Real text measurement, so a squeezed or clipped word shows.
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `at large text a long name grows the hero under its buttons instead of running into them`() {
        val name = "Kovácsné Szabó Erzsébet Mária Terézia Katalin Ilona"
        showDetail(PersonDetailUiState.Loaded(person(name, relationship = "Nagymama"), emptyList(), emptyList(), emptyList(), emptyList()), fontScale = 2f)

        val title = compose.onNodeWithText(name).getBoundsInRoot()
        val back = compose.onNodeWithContentDescription("Back").getBoundsInRoot()
        assertTrue("name ${title.top} below the buttons ${back.bottom}", title.top >= back.bottom)
        compose.onNodeWithText("Nagymama").assertIsDisplayed()
    }

    @Test
    fun `the next occasion leads the page with its countdown and plan, and Plan gift opens it`() {
        val idea = ideaEntry(ideaDetails(peter, name = "Earbuds").copy(occasionId = birthday.id), peter)
        showDetail(loaded(ideas = listOf(idea)))

        compose.onAllNodesWithText("Birthday")[0].assertExists()
        compose.onNodeWithText("October 12").assertExists()
        compose.onNodeWithText("22").assertExists()
        compose.onNodeWithText("1 idea").assertExists()

        compose.onNodeWithText("Plan gift").performClick()
        assertEquals(listOf("plan BIRTHDAY"), actions)
    }

    @Test
    fun `the history groups the gifts by year and occasion, newest first, as memory cards`() {
        val headphones = giftEntry(giftDetails(person = peter, name = "Headphones", givenAt = LocalDate.of(2026, 3, 1)), peter)
        val tickets = giftEntry(giftDetails(GiftDirection.RECEIVED, peter, "Concert tickets", LocalDate.of(2026, 2, 1)), peter)
        val shoes = giftEntry(giftDetails(person = peter, name = "Running shoes", givenAt = LocalDate.of(2025, 12, 24)), peter)
        showDetail(loaded(gifts = listOf(shoes, tickets, headphones)))

        compose.onNodeWithText("History with Peter").performScrollTo()
        compose.onNodeWithText("3 gifts shared together").assertExists()
        compose.onNodeWithText("2026").assertExists()
        compose.onNodeWithText("2025").assertExists()
        compose.onAllNodesWithText("Given to Peter", useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithText("Received from Peter", useUnmergedTree = true).assertCountEquals(1)

        compose.onNodeWithText("Concert tickets").performScrollTo().performClick()
        compose.onNodeWithText("Add gift").performScrollTo().performClick()
        assertEquals(listOf("gift Concert tickets", "add gift"), actions)
    }

    @Test
    fun `the page reads hero, next occasion, occasions, gift ideas, wishlists and history, each section ending in its add`() {
        val idea = ideaEntry(ideaDetails(peter, name = "Hiking poles"), peter)
        val gift = giftEntry(giftDetails(person = peter, name = "Headphones"), peter)
        showDetail(loaded(ideas = listOf(idea), gifts = listOf(gift), wishlists = listOf(christmasList)))

        val order = listOf(
            "Husband", "October 12", "Occasions", "Name day", "Add occasion", "Gift ideas", "Hiking poles", "Add idea",
            "Wishlists", "Christmas", "Add wishlist", "History with Peter", "Headphones",
        )
        val tops = order.map { compose.onNodeWithText(it).getBoundsInRoot().top }
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        listOf("Occasions", "Gift ideas", "Wishlists").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
        compose.onNodeWithText("Delete Peter").assertDoesNotExist()
    }

    @Test
    fun `gift ideas line up with the occasions and wishlists at the page's gutter`() {
        val idea = ideaEntry(ideaDetails(peter, name = "Hiking poles"), peter)
        showDetail(loaded(ideas = listOf(idea), wishlists = listOf(christmasList)))

        val left = listOf("Name day", "Hiking poles", "Christmas").map {
            compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot().left
        }
        left.forEach { assertNear(GiftiarySpacing.browseGutter, it, "a row's text") }
    }

    @Test
    fun `occasions and wishlists open from the page, and their adds are always there`() {
        showDetail(loaded(wishlists = listOf(christmasList)))

        compose.onNodeWithText("Name day").performClick()
        compose.onNodeWithText("Add occasion").performClick()
        compose.onNodeWithText("Christmas").performClick()
        compose.onNodeWithText("Add wishlist").performClick()

        assertEquals(listOf("edit occasion NAME_DAY", "add occasion", "wishlist", "add wishlist"), actions)
    }

    @Test
    fun `with nothing yet each section says so and still ends in its add`() {
        showDetail(loaded(occasions = emptyList()))

        compose.onNodeWithText("Plan gift").assertDoesNotExist()
        compose.onNodeWithText("No occasions yet").assertExists()
        compose.onNodeWithText("No wishlists for them yet.").assertExists()
        assertTrue("headed", top("Occasions") > top("Peter") && top("Occasions") < top("No occasions yet"))
        assertTrue("under the hero", top("Add occasion") < top("Gift ideas"))
        compose.onNodeWithText("Add occasion").performClick()
        compose.onNodeWithText("Add wishlist").performClick()

        assertEquals(listOf("add occasion", "add wishlist"), actions)
    }

    @Test
    fun `More holds only the secondary action, deleting, not the page's adds`() {
        showDetail(loaded(wishlists = listOf(christmasList)))

        compose.onNodeWithContentDescription("More for Peter").performClick()
        compose.onNodeWithText("Delete Peter").assertExists()
        // Only the page's own adds, none repeated in the sheet.
        compose.onAllNodesWithText("Add occasion").assertCountEquals(1)
        compose.onAllNodesWithText("Add wishlist").assertCountEquals(1)
        compose.onNodeWithText("OCCASIONS").assertDoesNotExist()
        compose.onNodeWithText("WISHLISTS").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h2000dp")
    fun `in Hungarian at large text the first occasion offer fits the screen`() {
        showDetail(loaded(occasions = emptyList()), fontScale = 2f)

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Még nincs alkalom", "Alkalom hozzáadása", "Kívánságlista hozzáadása").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).performScrollTo().getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }

    @Test
    @Config(qualifiers = "hu-w360dp-h3000dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian a section heading keeps clear of its subtitle, at large text too`() {
        assertHeadingsClear("Ajándékötletek", "Jövőbeli lehetőségek, még nem adtad oda")
    }

    @Test
    @Config(qualifiers = "w390dp-h3000dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in English a section heading keeps clear of its subtitle, at large text too`() {
        assertHeadingsClear("Gift ideas", "Future possibilities, not yet given")
    }

    private fun assertHeadingsClear(heading: String, subtitle: String) {
        showDetail(loaded())
        listOf(1f, 2f).forEach { scale ->
            fontScale = scale
            compose.waitForIdle()
            val title = compose.onNodeWithText(heading, useUnmergedTree = true).getBoundsInRoot()
            val line = compose.onNodeWithText(subtitle, useUnmergedTree = true).getBoundsInRoot()
            // Line boxes that merely touch still let a descender meet the accents below it.
            assertTrue("$heading at ${scale}x ends ${title.bottom}, subtitle starts ${line.top}", line.top - title.bottom >= 4.dp)
        }
    }

    @Test
    @Config(qualifiers = "hu-w360dp-h3000dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian the next occasion and its date sit whole beside the countdown, at 360dp`() {
        assertNextOccasionClean()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian the next occasion and its date sit whole beside the countdown, at 390dp`() {
        assertNextOccasionClean()
    }

    private fun assertNextOccasionClean() {
        val birthday = occasion(peter.id, OccasionDetails(OccasionType.BIRTHDAY, null, 16, 11, null, true))
        showDetail(loaded(occasions = listOf(birthday)), today = LocalDate.of(2026, 11, 8))
        listOf(1f, 1.3f, 2f).forEach { scale ->
            fontScale = scale
            compose.waitForIdle()
            val countdown = compose.onNodeWithText("8").getBoundsInRoot()
            val planGift = top("Ajándék tervezése")
            listOf("Születésnap", "november 16.").forEach {
                // The card's own line, above its Plan gift; the occasion's row further down reads the same.
                val node = compose.onAllNodesWithText(it, useUnmergedTree = true)[0]
                assertTrue("$it is on the card", node.getBoundsInRoot().top < planGift)
                assertEquals("$it at ${scale}x is one line", 1, node.lineCount())
                assertTrue("$it at ${scale}x ends before the countdown", node.getBoundsInRoot().right <= countdown.left)
            }
        }
    }

    @Test
    fun `deleting the person is in More, last, and asks first`() {
        showDetail(loaded())

        compose.onNodeWithContentDescription("More for Peter").performClick()
        compose.onNodeWithText("Delete Peter").performScrollTo().performClick()
        compose.onNodeWithText("Delete Peter?").assertExists()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf("deleted"), actions)
    }

    @Test
    fun `the next occasion is the soonest still ahead, and history groups are newest first`() {
        assertEquals(birthday, nextOccasion(listOf(nameDay, birthday), today)?.first)
        assertEquals(nameDay, nextOccasion(listOf(nameDay, birthday), LocalDate.of(2026, 10, 13))?.first)

        val a = giftEntry(giftDetails(person = peter, name = "A", givenAt = LocalDate.of(2025, 5, 1)), peter)
        val b = giftEntry(giftDetails(person = peter, name = "B", givenAt = LocalDate.of(2026, 5, 1)), peter)
        val c = giftEntry(giftDetails(person = peter, name = "C", givenAt = LocalDate.of(2026, 1, 1)), peter)
        val groups = historyGroups(listOf(a, b, c))
        assertEquals(listOf(2026, 2025), groups.map { it.year })
        assertEquals(listOf("B", "C"), groups.first().gifts.map { it.gift.name })
    }
}
