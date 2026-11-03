package app.giftiary.presentation.occasions

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.model.Person
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class OccasionPlanScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter")
    private val anna = person("Anna")
    private val birthday = occasion(peter.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, 1990, true))
    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
    private val scarf = ideaEntry(ideaDetails(anna, name = "Scarf"), recipient = anna)

    private val calls = mutableListOf<String>()

    private fun show(state: OccasionPlanUiState) {
        compose.setContent {
            GiftiaryTheme {
                OccasionPlanScreen(
                    state = state,
                    onBack = { calls += "back" },
                    onEditOccasion = { calls += "edit ${it.type}" },
                    onOpenPerson = { calls += "person ${it.name}" },
                    onOpenGiftIdea = { calls += "idea ${it.idea.name}" },
                    onAddGiftIdea = { calls += "add ${it.type}" },
                )
            }
        }
    }

    private fun loaded(
        occasion: Occasion,
        person: Person?,
        ideas: OccasionIdeas,
        daysUntil: Int? = 12,
        turningAge: Int? = null,
    ) = OccasionPlanUiState.Loaded(
        occasion = occasion,
        person = person,
        nextDate = daysUntil?.let { LocalDate.of(2026, occasion.month, occasion.day) },
        daysUntil = daysUntil,
        turningAge = turningAge,
        plan = null,
        ideas = ideas,
    )

    private fun ideas(vararg entries: GiftIdeaEntry) = OccasionIdeas.Loaded(entries.toList())

    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun `a person's occasion shows them and opens their page`() {
        show(loaded(birthday, peter, ideas(), turningAge = 36))

        compose.onNodeWithText("Birthday").assertExists()
        compose.onNodeWithText("turns 36", substring = true).assertExists()
        compose.onNodeWithText("12").assertExists()

        val card = compose.onNode(hasText("Peter"), useUnmergedTree = false)
        assertEquals("Open", card.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        card.performClick()
        assertEquals(listOf("person Peter"), calls)
    }

    @Test
    fun `a general occasion shows no one, and each idea says whose it is`() {
        show(loaded(christmas, null, ideas(scarf)))

        compose.onNodeWithText("Christmas").assertExists()
        // Anna appears once, on her idea: there is no person card.
        compose.onAllNodesWithText("Anna", substring = true).assertCountEquals(1)

        compose.onNodeWithText("Scarf").performClick()
        assertEquals(listOf("idea Scarf"), calls)
    }

    @Test
    fun `a person's own occasion does not repeat their name on each idea`() {
        val forPeter = ideaEntry(ideaDetails(peter, name = "Book"), recipient = peter)
        show(loaded(birthday, peter, ideas(forPeter)))

        compose.onAllNodesWithText("Peter", substring = true).assertCountEquals(1)
    }

    @Test
    fun `no ideas and unreadable ideas are told apart`() {
        show(loaded(christmas, null, ideas()))
        compose.onNodeWithText("No ideas linked to this occasion yet.").assertExists()
        compose.onNodeWithText("The ideas for this occasion cannot be shown right now.").assertDoesNotExist()
    }

    @Test
    fun `unreadable ideas say so instead of claiming there are none`() {
        show(loaded(christmas, null, OccasionIdeas.Unavailable))
        compose.onNodeWithText("The ideas for this occasion cannot be shown right now.").assertExists()
        compose.onNodeWithText("No ideas linked to this occasion yet.").assertDoesNotExist()
    }

    @Test
    fun `Add gift idea plans for this occasion and Edit stays in the menu`() {
        show(loaded(christmas, null, ideas()))

        compose.onNodeWithText("Add gift idea").performClick()
        compose.onNodeWithText("Edit occasion").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Edit occasion").performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("add CHRISTMAS", "edit CHRISTMAS", "back"), calls)
    }

    @Test
    fun `the occasion and its ideas are the page's headings`() {
        show(loaded(christmas, null, ideas()))

        compose.onAllNodes(heading).assertCountEquals(2)
        compose.onNode(hasText("Christmas") and heading).assertExists()
        compose.onNode(hasText("Gift ideas") and heading).assertExists()
    }

    @Test
    fun `a one-time occasion that has passed shows its date and no countdown`() {
        val graduation = occasion(null, OccasionDetails(OccasionType.CUSTOM, "Graduation", 20, 6, 2020, false))
        show(loaded(graduation, null, ideas(), daysUntil = null))

        compose.onNodeWithText("One-time", substring = true).assertExists()
        compose.onNodeWithText("Add gift idea").assertExists()
    }

    @Test
    fun `a missing occasion says so and keeps the way back`() {
        show(OccasionPlanUiState.NotFound)

        compose.onNodeWithText("This occasion no longer exists.").assertExists()
        compose.onAllNodesWithText("Add gift idea").assertCountEquals(0)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}
