package app.giftiary.presentation.onboarding

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import app.giftiary.R
import app.giftiary.presentation.theme.GiftiaryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Remember, Plan, Share; Continue, Skip and Back, each with one outcome. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
// Real text measurement, so whether a chapter fits the screen is measured, not guessed.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h844dp")
class OnboardingScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private var done = 0

    private fun show() {
        compose.setContent { GiftiaryTheme { OnboardingScreen(onDone = { done++ }) } }
    }

    private fun back() = compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }

    /** How far the chapter can scroll: 0 when it fits the screen. */
    private fun scrollRange(): Float = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    private fun assertEveryChapterFits(next: String) {
        show()
        repeat(3) { chapter ->
            assertEquals("chapter ${chapter + 1} fits without scrolling", 0f, scrollRange())
            if (chapter < 2) compose.onNodeWithText(next).performClick()
        }
    }

    private val pages get() = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange))

    private fun swipeLeft() = pages.performTouchInput { swipeLeft() }

    private fun swipeRight() = pages.performTouchInput { swipeRight() }

    private fun assertChapter(position: Int, title: String) {
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithContentDescription("Step $position of 3").assertExists()
    }

    @Test
    fun `Continue walks Remember, Plan and Share in order, and Get started finishes once`() {
        show()

        assertChapter(1, "Never forget a gift again.")
        compose.onNodeWithText("REMEMBER").assertExists()
        compose.onNodeWithText("Continue").performClick()
        assertChapter(2, "Be ready for the people who matter.")
        compose.onNodeWithText("Continue").performClick()
        assertChapter(3, "Share wishes. Keep the surprise.")
        compose.onNodeWithText("Reservations stay hidden from the list owner.").assertExists()
        compose.onNodeWithText("Continue").assertDoesNotExist()
        assertEquals(0, done)

        compose.onNodeWithText("Get started").performClick()
        assertEquals(1, done)
    }

    @Test
    fun `swiping left and right pages through the chapters, held at either end`() {
        show()

        swipeRight()
        assertChapter(1, "Never forget a gift again.")
        swipeLeft()
        assertChapter(2, "Be ready for the people who matter.")
        swipeLeft()
        assertChapter(3, "Share wishes. Keep the surprise.")
        compose.onNodeWithText("Get started").assertExists()
        swipeLeft()
        assertChapter(3, "Share wishes. Keep the surprise.")
        assertEquals("only Get started finishes", 0, done)
        swipeRight()
        assertChapter(2, "Be ready for the people who matter.")
        compose.onNodeWithText("Continue").assertExists()
    }

    @Test
    fun `swipes, Continue and Back move the same pages`() {
        show()

        swipeLeft()
        compose.onNodeWithText("Continue").performClick()
        assertChapter(3, "Share wishes. Keep the surprise.")
        back()
        assertChapter(2, "Be ready for the people who matter.")
        swipeRight()
        assertChapter(1, "Never forget a gift again.")
        compose.onNodeWithText("Continue").performClick()
        swipeLeft()
        compose.onNodeWithText("Get started").performClick()
        assertEquals(1, done)
    }

    @Test
    fun `Skip finishes from any chapter`() {
        show()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(1, done)

        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Skip").performClick()
        assertEquals(2, done)
    }

    @Test
    fun `Back returns a chapter at a time, and on the first is left to the system, so it cannot loop`() {
        show()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Continue").performClick()

        back()
        assertChapter(2, "Be ready for the people who matter.")
        back()
        assertChapter(1, "Never forget a gift again.")
        assertFalse(compose.runOnIdle { compose.activity.onBackPressedDispatcher.hasEnabledCallbacks() })
        assertEquals("Back never finishes the introduction", 0, done)
    }

    @Test
    fun `there is no product navigation, and the chapter images are silent`() {
        show()

        listOf("Home", "People", "Gifts", "Lists").forEach { compose.onNodeWithText(it).assertDoesNotExist() }
        // Only the progress speaks beyond the text: the words say what each image shows.
        repeat(3) { chapter ->
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription), useUnmergedTree = true)
                .assertCountEquals(1)
            if (chapter < 2) compose.onNodeWithText("Continue").performClick()
        }
    }

    @Test
    fun `each chapter shows its owner-approved image`() {
        assertEquals(R.drawable.giftiary_gift_cutout_large, OnboardingChapter.REMEMBER.image)
        assertEquals(R.drawable.giftiary_gift_cutout_small, OnboardingChapter.PLAN.image)
        assertEquals(R.drawable.giftiary_onboarding_share, OnboardingChapter.SHARE.image)
    }

    // A wide phone (e.g. 412×892) less its status and navigation bars, where a width-sized image
    // used to push the words off the screen.
    @Test
    @Config(qualifiers = "w412dp-h790dp")
    fun `on a wide phone every chapter fits without scrolling`() = assertEveryChapterFits("Continue")

    // A common phone, less its status and navigation bars.
    @Test
    @Config(qualifiers = "w360dp-h720dp")
    fun `on a common phone every chapter fits without scrolling`() = assertEveryChapterFits("Continue")

    @Test
    @Config(qualifiers = "hu-w360dp-h720dp")
    fun `in Hungarian on a common phone every chapter fits without scrolling`() = assertEveryChapterFits("Tovább")

    @Test
    @Config(qualifiers = "w320dp-h520dp")
    fun `a small screen scrolls the chapter but keeps Continue and the progress on screen`() {
        show()

        assertTrue("the chapter scrolls", scrollRange() > 0f)
        val root = compose.onRoot().getBoundsInRoot()
        assertTrue(compose.onNodeWithText("Continue").getBoundsInRoot().bottom <= root.bottom)
        compose.onNodeWithContentDescription("Step 1 of 3").assertExists()

        // An upright drag scrolls the chapter, never the pages; a sideways one still pages.
        val chapter = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        chapter.performTouchInput { swipeUp() }
        assertTrue("scrolled", chapter.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        compose.onNodeWithContentDescription("Step 1 of 3").assertExists()
        swipeLeft()
        compose.onNodeWithContentDescription("Step 2 of 3").assertExists()
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h844dp")
    fun `Hungarian at large text stays inside the screen and keeps Continue reachable`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { OnboardingScreen(onDone = { done++ }) }
            }
        }

        val root = compose.onRoot().getBoundsInRoot()
        listOf("Kihagyás", "Soha többé ne felejts el egy ajándékot sem.", "Tovább").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= ${root.right}", bounds.right <= root.right)
        }
        assertTrue(compose.onNodeWithText("Tovább").getBoundsInRoot().bottom <= root.bottom)
        compose.onNodeWithText("Tovább").performClick()
        compose.onNodeWithText("Légy felkészült azokra, akik fontosak.").assertExists()
    }
}
