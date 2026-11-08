package app.giftiary.presentation.profile

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ApplicationProvider
import app.giftiary.R
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.people.PERSON_PHOTO_TAG
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
import java.io.ByteArrayOutputStream

/** Profile shows what the account has: avatar or monogram, name or email, counts, then Settings and Sign out. */
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

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the saved avatar takes the monogram's place, silently, beside the name`() {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF5A4032.toInt()) }
        show(full.copy(avatar = LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())))

        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithText("Anna Kovács").assertExists()
    }

    @Test
    fun `without an avatar the monogram stays`() {
        show(full)

        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
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
        listOf("Profil szerkesztése", "TÖRTÉNETED", "Beállítások", "Kijelentkezés").forEach {
            val bounds = compose.onNodeWithText(it, substring = true, useUnmergedTree = true).getBoundsInRoot()
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

    @Test
    @Config(qualifiers = "hu-w360dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the Hungarian story heading breaks only between words, at 360dp`() = assertStoryHeadingWhole()

    @Test
    @Config(qualifiers = "hu-w390dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the Hungarian story heading breaks only between words, at 390dp`() = assertStoryHeadingWhole()

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `the English story heading breaks only between words, at 360dp`() = assertStoryHeadingWhole()

    private fun assertStoryHeadingWhole() {
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                GiftiaryTheme { ProfileContent(full, ProfileActions({}, {}, {}, {})) }
            }
        }
        // In between too, where the heading wraps although its long word would still fit on a line.
        (10..20).map { it / 10f }.forEach { s ->
            scale = s
            compose.waitForIdle()
            val heading = compose.onNodeWithText("GIFTIARY", substring = true, useUnmergedTree = true)
            val layout = heading.textLayout()
            val text = layout.layoutInput.text.text
            (0 until layout.lineCount - 1).forEach { line ->
                val end = layout.getLineEnd(line)
                assertTrue("at ${s}x line ${line + 1} of \"$text\" ends at a space, not inside a word", text[end - 1] == ' ')
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a long email keeps clear of the corner gift at 360dp`() = assertEmailClearOfGift()

    @Test
    @Config(qualifiers = "hu-w360dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `in Hungarian a long email keeps clear of the corner gift at 360dp`() = assertEmailClearOfGift()

    @Test
    @Config(qualifiers = "w390dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a long email keeps clear of the corner gift at 390dp`() = assertEmailClearOfGift()

    /**
     * Measured against the drawing itself: for every pixel row the email's line spans, where the
     * gift's painted part starts, as [DecoratedPage] places the small cut-out.
     */
    private fun assertEmailClearOfGift() {
        val email = "someone.with.a.long.address@example.com"
        var current by mutableStateOf(ProfileUiState(email = email))
        var scale by mutableFloatStateOf(1f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                GiftiaryTheme { ProfileContent(current, ProfileActions({}, {}, {}, {})) }
            }
        }
        val gift = BitmapFactory.decodeResource(
            ApplicationProvider.getApplicationContext<Context>().resources,
            R.drawable.giftiary_gift_cutout_small,
        )
        listOf(null, "Timi").forEach { name ->
            listOf(1f, 2f).forEach { s ->
                current = ProfileUiState(email = email, displayName = name)
                scale = s
                compose.waitForIdle()
                val density = compose.density.density
                val root = compose.onRoot().getBoundsInRoot()
                val text = compose.onNodeWithText(email, useUnmergedTree = true).getBoundsInRoot()
                val box = 200f * density
                val boxLeft = root.right.value * density - 120f * density
                val boxTop = -24f * density
                val giftLeft = ((text.top.value * density).toInt()..(text.bottom.value * density).toInt()).mapNotNull { y ->
                    val row = ((y - boxTop) / box * gift.height).toInt()
                    if (row !in 0 until gift.height) return@mapNotNull null
                    (0 until gift.width).firstOrNull { Color.alpha(gift.getPixel(it, row)) > 40 }
                }.minOrNull()?.let { (boxLeft + it * box / gift.width) / density } ?: return@forEach
                val clearance = giftLeft - text.right.value
                assertTrue("named $name at ${s}x: ${clearance}dp between the email and the gift", clearance >= 8f)
            }
        }
    }

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
