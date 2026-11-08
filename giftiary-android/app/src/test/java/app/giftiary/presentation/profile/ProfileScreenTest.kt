package app.giftiary.presentation.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.lineCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Profile shows what the account has: monogram, name or email, counts, then Settings and Sign out. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1400dp")
class ProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val full = ProfileUiState(email = "anna@example.com", displayName = "Anna Kovács", stats = ProfileStats(4, 12, 1))
    private val events = mutableListOf<String>()
    private var state by mutableStateOf(full)
    private var shown = false

    private fun show(state: ProfileUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            GiftiaryTheme {
                ProfileContent(
                    state = this.state,
                    actions = ProfileActions(
                        onBack = { events += "back" },
                        onEditProfile = { events += "edit" },
                        onOpenSettings = { events += "settings" },
                        onSignOut = { events += "sign out" },
                    ),
                )
            }
        }
    }

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    @Test
    fun `identity, Edit profile, the story, then Settings and Sign out, top to bottom`() {
        show(full)

        val order = listOf("Anna Kovács", "anna@example.com", "Edit profile", "YOUR GIFTIARY STORY", "Settings", "Sign out")
        val tops = order.map(::top)
        assertEquals("top to bottom: $order", tops.sorted(), tops)
        // The monogram is decoration beside the written name, so screen readers do not read it twice.
        compose.onNodeWithText("A", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the counts are the device's own, with their plurals`() {
        show(full)

        listOf("4", "people", "12", "gift memories", "1", "list").forEach { compose.onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun `without a name the email stands alone, written once`() {
        show(full.copy(displayName = null))

        assertEquals(1, compose.onAllNodesWithText("anna@example.com").fetchSemanticsNodes().size)
    }

    @Test
    fun `nothing is counted before the counts are read`() {
        show(full.copy(stats = null))

        compose.onNodeWithText("YOUR GIFTIARY STORY").assertDoesNotExist()
        compose.onNodeWithText("Settings").assertExists()
    }

    @Test
    fun `each action reaches the flow`() {
        show(full)

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Edit profile").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Sign out").performClick()

        assertEquals(listOf("back", "edit", "settings", "sign out"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h1400dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme { ProfileContent(full, ProfileActions({}, {}, {}, {})) }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Profil szerkesztése", "A GIFTIARY-TÖRTÉNETED", "Beállítások", "Kijelentkezés").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a long email stays on one line, read out whole, at 390dp and large text`() = assertLongEmailOneLine()

    @Test
    @Config(qualifiers = "hu-w360dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian a long email stays on one line, read out whole, at 360dp and large text`() = assertLongEmailOneLine()

    private fun assertLongEmailOneLine() {
        val email = "giftiary.owner.tests@example.com"
        var current by mutableStateOf(ProfileUiState(email = email, displayName = null))
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                GiftiaryTheme { ProfileContent(current, ProfileActions({}, {}, {}, {})) }
            }
        }
        listOf(null, "Timi").forEach { name ->
            listOf(1f, 2f).forEach { s ->
                current = ProfileUiState(email = email, displayName = name)
                scale = s
                compose.waitForIdle()
                val node = compose.onNodeWithText(email, useUnmergedTree = true)
                assertEquals("named $name at ${s}x", 1, node.lineCount())
                assertTrue(node.getBoundsInRoot().right <= compose.onRoot().getBoundsInRoot().right)
            }
        }
    }
}
