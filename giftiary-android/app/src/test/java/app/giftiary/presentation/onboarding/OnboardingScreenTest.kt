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

/** Remember, Plan, Share; Continue, Skip and Back, each with one outcome. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
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
