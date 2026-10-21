package app.giftiary.presentation.gifts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.usecase.GiftInputError
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Where the gift form puts its fields at each width class, measured on the laid-out editor. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp")
class GiftEditorLayoutTest {

    @get:Rule
    val compose = createComposeRule()

    private val anna = person("Anna")
    private val editing = GiftEditorUiState(
        isNew = true,
        status = GiftEditorStatus.EDITING,
        givenAt = LocalDate.of(2026, 3, 12),
        direction = GiftDirection.GIVEN,
        personId = anna.id,
        activePeople = listOf(anna),
        peopleLoaded = true,
        name = "Lego",
        price = "12500",
        currencyCode = "HUF",
        productUrl = "https://example.org/lego",
        note = "Wrapped in blue",
    )

    // The form from top to bottom on a phone; on a wide window the same parts, split into columns.
    private val directionLabel = "Given"
    private val personLabel = "Recipient"
    private val nameLabel = "Gift"
    private val occasionLabel = "Occasion"
    private val dateLabel = "Date"
    private val priceLabel = "Price (optional)"
    private val linkLabel = "Product link (optional)"
    private val noteLabel = "Note (optional)"
    private val save = "Save"
    private val leftColumn = listOf(directionLabel, personLabel, nameLabel, occasionLabel, dateLabel)
    private val rightColumn = listOf(priceLabel, linkLabel, noteLabel)

    private var state by mutableStateOf(editing)
    private var widthClass by mutableStateOf(WidthClass.COMPACT)
    private var callbacks = 0

    private fun showEditor(width: WidthClass, editorState: GiftEditorUiState = editing, height: Dp? = null) {
        widthClass = width
        state = editorState
        val count = { callbacks++ }
        compose.setContent {
            GiftiaryTheme {
                Box(Modifier.width(widthOf(widthClass)).then(if (height != null) Modifier.height(height) else Modifier.fillMaxHeight())) {
                    GiftEditorScreen(
                        state = state,
                        actions = GiftEditorActions(
                            onBack = { count() },
                            onOpenPeople = { count() },
                            onDirectionChange = { count() },
                            onPersonChange = { count() },
                            onOccasionChange = { count() },
                            onNameChange = { count() },
                            onDateChange = { count() },
                            onPriceChange = { count() },
                            onProductUrlChange = { count() },
                            onNoteChange = { count() },
                            onSave = { count() },
                            onWriteErrorShown = { count() },
                        ),
                        widthClass = widthClass,
                    )
                }
            }
        }
    }

    private fun widthOf(widthClass: WidthClass): Dp = when (widthClass) {
        WidthClass.COMPACT -> 411.dp
        WidthClass.MEDIUM -> 800.dp
        WidthClass.EXPANDED -> 1200.dp
    }

    // A label's own text; the merged node would be the whole field.
    private fun label(text: String): DpRect = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot()

    private fun button(text: String): DpRect = compose.onNodeWithText(text).getBoundsInRoot()

    private val DpRect.widthDp: Dp get() = right - left

    private fun assertNear(expected: Dp, actual: Dp, what: String) =
        assertTrue("$what: expected about $expected, was $actual", (actual - expected).value in -1f..1f)

    private fun assertTopToBottom(texts: List<String>) {
        val tops = texts.map { if (it == save) button(it).top else label(it).top }
        assertEquals("top to bottom: $texts", tops.sorted(), tops)
    }

    /** The editable fields' values in semantics order, which is also the order focus and TalkBack follow. */
    private fun fieldValuesInOrder(): List<String> =
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText))
            .fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty() }

    @Test
    fun `a phone keeps one column, in the usual order, with a full-width save button`() {
        showEditor(WidthClass.COMPACT)

        assertTopToBottom(leftColumn + rightColumn + save)
        // The usual 24 dp margins on both sides.
        assertNear(24.dp, button(save).left, "save start")
        assertNear(411.dp - 48.dp, button(save).widthDp, "save width")
    }

    @Test
    fun `a medium window shows the same column at a readable, centred width`() {
        showEditor(WidthClass.MEDIUM)

        assertTopToBottom(leftColumn + rightColumn + save)
        // 640 dp centred in 800 dp starts at 80 dp, plus the 24 dp margin.
        assertNear(104.dp, button(save).left, "save start")
        assertNear(640.dp - 48.dp, button(save).widthDp, "save width")
    }

    @Test
    fun `an expanded window puts who, what and when left, the details right and save below both`() {
        showEditor(WidthClass.EXPANDED)

        assertTopToBottom(leftColumn)
        assertTopToBottom(rightColumn)
        val name = label(nameLabel)
        rightColumn.forEach { assertTrue("$it in the right column", label(it).left > name.left + 400.dp) }
        assertTrue("the right column starts at the top, beside the direction", label(priceLabel).top < label(personLabel).top)

        val saveButton = button(save)
        val lowestField = (leftColumn + rightColumn).maxOf { label(it).bottom }
        assertTrue("save below both columns", saveButton.top > lowestField)
        // 1040 dp centred in 1200 dp ends at 1120 dp, less the 24 dp margin.
        assertNear(1096.dp, saveButton.right, "save end")
        assertTrue("save at its natural width (was ${saveButton.widthDp})", saveButton.widthDp < 300.dp)
    }

    @Test
    fun `on a short wide window the whole form scrolls, so save stays reachable`() {
        // About a landscape tablet with the keyboard up.
        showEditor(WidthClass.EXPANDED, height = 360.dp)

        compose.onNodeWithText(save).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(noteLabel, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `errors stay under their field in either column`() {
        showEditor(
            WidthClass.EXPANDED,
            editing.copy(name = "", errors = setOf(GiftInputError.NAME_REQUIRED), priceError = PriceError.INVALID),
        )

        val nameError = label("Enter what the gift was")
        val priceError = label("Enter a valid price")
        assertTrue("name error in the left column", nameError.right < priceError.left)
        assertTrue("name error under the name", nameError.top > label(nameLabel).top && nameError.top < label(occasionLabel).top)
        assertTrue("price error under the price", priceError.top > label(priceLabel).top && priceError.top < label(linkLabel).top)
    }

    @Test
    fun `with nobody to choose, a wide window keeps one readable column and no details`() {
        showEditor(WidthClass.EXPANDED, editing.copy(personId = null, activePeople = emptyList()))

        val message = label("Gifts are given to or received from people. Add someone first.")
        // 640 dp centred in 1200 dp starts at 280 dp, plus the 24 dp margin.
        assertNear(304.dp, message.left, "message start")
        compose.onNodeWithText("Go to People").assertExists()
        compose.onNodeWithText(priceLabel).assertDoesNotExist()
        compose.onNodeWithText(save).assertDoesNotExist()
    }

    @Test
    fun `the fields keep the phone's order for focus and screen readers on a wide window`() {
        showEditor(WidthClass.COMPACT)
        val compact = fieldValuesInOrder()

        widthClass = WidthClass.EXPANDED
        compose.waitForIdle()

        assertEquals(7, compact.size)
        assertEquals(compact, fieldValuesInOrder())
    }

    @Test
    fun `a live resize keeps every value, error and the busy state, without any callback`() {
        showEditor(
            WidthClass.COMPACT,
            editing.copy(errors = setOf(GiftInputError.NAME_REQUIRED), priceError = PriceError.INVALID, isBusy = true),
        )

        for (width in listOf(WidthClass.EXPANDED, WidthClass.MEDIUM, WidthClass.COMPACT, WidthClass.EXPANDED)) {
            widthClass = width
            compose.waitForIdle()
            listOf("Lego", "12500", "https://example.org/lego", "Wrapped in blue").forEach {
                compose.onAllNodesWithText(it, useUnmergedTree = true).fetchSemanticsNodes().let { nodes ->
                    assertTrue("$it shown at $width", nodes.isNotEmpty())
                }
            }
            compose.onNodeWithText("Enter what the gift was", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Enter a valid price", useUnmergedTree = true).assertExists()
            compose.onNodeWithText("Lego").assertIsNotEnabled()
        }
        assertEquals("no callback or navigation", 0, callbacks)
    }
}
