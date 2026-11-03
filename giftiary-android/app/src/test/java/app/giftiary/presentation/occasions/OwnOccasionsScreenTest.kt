package app.giftiary.presentation.occasions

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.giftiary.domain.model.Occasion
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.occasion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class OwnOccasionsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val christmas = occasion(null, OccasionDetails(OccasionType.CHRISTMAS, null, 24, 12, null, true))
    private val graduation = occasion(null, OccasionDetails(OccasionType.CUSTOM, "Graduation", 20, 6, 2027, false))

    private var backs = 0
    private var adds = 0
    private val opened = mutableListOf<Occasion>()

    private fun show(content: OwnOccasionsContent) {
        compose.setContent {
            GiftiaryTheme {
                OwnOccasionsScreen(
                    content = content,
                    onBack = { backs++ },
                    onAddOccasion = { adds++ },
                    onOpenOccasion = { opened += it },
                )
            }
        }
    }

    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    @Test
    fun `the list opens each occasion and keeps Add occasion under the title`() {
        show(OwnOccasionsContent.Loaded(listOf(christmas, graduation)))

        compose.onNodeWithText("Your occasions").assertExists()
        compose.onAllNodes(heading).assertCountEquals(1)
        compose.onNodeWithText("One-time", substring = true).assertExists()

        val row = compose.onNodeWithText("Graduation")
        assertEquals("Open", row.fetchSemanticsNode().config.getOrNull(SemanticsActions.OnClick)?.label)
        row.performClick()
        assertEquals(listOf(graduation), opened)

        compose.onAllNodesWithText("Add occasion").assertCountEquals(1)
        compose.onNodeWithText("Add occasion").performClick()
        assertEquals(1, adds)

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `the empty state has one Add occasion`() {
        show(OwnOccasionsContent.Loaded(emptyList()))

        compose.onNodeWithText("No occasions of your own yet").assertExists()
        compose.onAllNodes(heading).assertCountEquals(2)
        compose.onAllNodesWithText("Add occasion").assertCountEquals(1)
        compose.onNodeWithText("Add occasion").performClick()
        assertEquals(1, adds)
    }

    @Test
    fun `a failed load says so and keeps the way back, with nothing to add to`() {
        show(OwnOccasionsContent.LoadFailed)

        compose.onNodeWithText("Your occasions cannot be shown right now.").assertExists()
        compose.onAllNodesWithText("Add occasion").assertCountEquals(0)
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun `while loading only the way back is offered`() {
        show(OwnOccasionsContent.Loading)

        compose.onNodeWithContentDescription("Back").assertExists()
        compose.onAllNodesWithText("Add occasion").assertCountEquals(0)
    }
}
