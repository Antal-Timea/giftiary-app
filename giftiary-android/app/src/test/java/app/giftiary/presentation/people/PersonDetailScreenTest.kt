package app.giftiary.presentation.people

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Wishlist
import app.giftiary.domain.repository.GiftEntry
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.WithFontScale
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
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

    private fun showDetail(state: PersonDetailUiState.Loaded, fontScale: Float = 1f) {
        compose.setContent {
            WithFontScale(fontScale) {
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
                    )
                }
            }
        }
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

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Edit Peter").performClick()
        assertEquals(listOf("back", "edit Peter"), actions)
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

        compose.onNodeWithText("Birthday · October 12").assertExists()
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
    fun `the page keeps the approved order of hero, occasion, gift ideas and history, with nothing between`() {
        val idea = ideaEntry(ideaDetails(peter, name = "Hiking poles"), peter)
        val gift = giftEntry(giftDetails(person = peter, name = "Headphones"), peter)
        showDetail(loaded(ideas = listOf(idea), gifts = listOf(gift), wishlists = listOf(christmasList)))

        val order = listOf("Husband", "Birthday · October 12", "Gift ideas", "Hiking poles", "History with Peter", "Headphones")
        val tops = order.map { compose.onNodeWithText(it).getBoundsInRoot().top }
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        // Occasions, wishlists and deleting are secondary: in the More sheet, not on the page.
        listOf("Occasions", "Name day", "Wishlists", "Christmas", "Add occasion", "Add wishlist", "Delete Peter").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithContentDescription("More for Peter").assertExists()
        compose.onNodeWithText("Hiking poles").performClick()
        compose.onNodeWithText("Add idea").performClick()
        assertEquals(listOf("idea Hiking poles", "add idea"), actions)
    }

    @Test
    fun `More holds the occasions, and each choice closes it`() {
        showDetail(loaded())

        compose.onNodeWithContentDescription("More for Peter").performClick()
        compose.onNodeWithText("OCCASIONS").assertExists()
        compose.onNodeWithText("Name day").performClick()
        compose.onNodeWithText("OCCASIONS").assertDoesNotExist()

        compose.onNodeWithContentDescription("More for Peter").performClick()
        compose.onNodeWithText("Add occasion").performClick()

        assertEquals(listOf("edit occasion NAME_DAY", "add occasion"), actions)
    }

    @Test
    fun `with no occasions the page itself offers the first one, under the hero, and More still adds one`() {
        showDetail(loaded(occasions = emptyList()))

        compose.onNodeWithText("Plan gift").assertDoesNotExist()
        compose.onNodeWithText("No occasions yet").assertExists()
        assertTrue("under the hero", top("No occasions yet") > top("Peter") && top("Add occasion") < top("Gift ideas"))
        // A section of its own, headed like Gift ideas.
        assertTrue("headed", top("Occasions") > top("Peter") && top("Occasions") < top("No occasions yet"))
        compose.onNodeWithText("Occasions", useUnmergedTree = true).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        compose.onNodeWithText("Add occasion").performClick()
        compose.onNodeWithContentDescription("More for Peter").performClick()
        compose.onAllNodesWithText("Add occasion").assertCountEquals(2)[1].performClick()

        assertEquals(listOf("add occasion", "add occasion"), actions)
    }

    @Test
    fun `once there are occasions, adding more stays in More`() {
        showDetail(loaded())

        compose.onNodeWithText("No occasions yet").assertDoesNotExist()
        compose.onNodeWithText("Add occasion").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h2000dp")
    fun `in Hungarian at large text the first occasion offer fits the screen`() {
        showDetail(loaded(occasions = emptyList()), fontScale = 2f)

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Még nincs alkalom", "Alkalom hozzáadása").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }

    @Test
    fun `More holds the person's wishlists and adds one`() {
        showDetail(loaded(wishlists = listOf(christmasList)))

        compose.onNodeWithContentDescription("More for Peter").performClick()
        compose.onNodeWithText("Christmas").performClick()
        compose.onNodeWithContentDescription("More for Peter").performClick()
        compose.onNodeWithText("Add wishlist").performClick()

        assertEquals(listOf("wishlist", "add wishlist"), actions)
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
