package app.giftiary.presentation.profile

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import app.giftiary.presentation.gifts.LoadedPhoto
import app.giftiary.presentation.people.PERSON_PHOTO_TAG
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.awaitPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** Edit profile: the avatar with its actions above the name, and what a failed change says. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1400dp")
class EditProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<String>()
    private val loaded = EditProfileUiState(profile = ProfileContent.Loaded(saved = "Anna"), nameInput = "Anna", email = "anna@example.com")

    private fun show(state: EditProfileUiState, fontScale: Float = 1f) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                GiftiaryTheme {
                    EditProfileContent(
                        state = state,
                        actions = EditProfileActions({}, {}, {}, {}, {}),
                        avatar = EditProfileAvatarActions(
                            onRemove = { actions += "remove" },
                            onRetry = { actions += "retry" },
                        ),
                    )
                }
            }
        }
    }

    @Test
    fun `without an avatar there is the monogram, Add photo, and that only the user sees it`() {
        show(loaded)

        compose.onNodeWithText("Add photo").assertExists()
        compose.onNodeWithText("Only you see your profile photo.").assertExists()
        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Remove photo").assertDoesNotExist()
        val photoTop = compose.onNodeWithText("Add photo").getBoundsInRoot().top
        assertTrue("above the name", photoTop < compose.onNodeWithText("Your name").getBoundsInRoot().top)
    }

    @Test
    fun `Add photo asks camera or gallery, and cancelling there changes nothing`() {
        show(loaded)

        compose.onNodeWithText("Add photo").performClick()
        compose.onNodeWithText("Take photo").assertExists()
        compose.onNodeWithText("Choose from gallery").assertExists()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("Take photo").assertDoesNotExist()
        assertTrue(actions.isEmpty())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `with an avatar it fills the circle, silently, and Change and Remove are offered`() {
        show(loaded.copy(avatarPath = "p.jpg", avatar = jpeg()))

        compose.awaitPhoto(PERSON_PHOTO_TAG)
        compose.onNodeWithTag(PERSON_PHOTO_TAG, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
        compose.onNodeWithText("Change photo").assertExists()
        compose.onNodeWithText("Remove photo").performClick()
        assertEquals(listOf("remove"), actions)
    }

    @Test
    fun `while a change is on its way the actions wait`() {
        show(loaded.copy(avatarPath = "p.jpg", avatarBusy = true))

        compose.onNodeWithText("Change photo").assertIsNotEnabled()
        compose.onNodeWithText("Remove photo").assertIsNotEnabled()
    }

    @Test
    fun `a failed change says the photo did not change, and offers to try again`() {
        show(loaded.copy(avatarPath = "old.jpg", avatarFailure = AvatarFailure.NETWORK, retry = AvatarChange.Set("new.jpg")))

        compose.onNodeWithText("Your profile photo didn’t change.", substring = true).assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry"), actions)
    }

    @Test
    fun `an unreadable image is said without a retry`() {
        show(loaded.copy(avatarFailure = AvatarFailure.UNREADABLE))

        compose.onNodeWithText("This image couldn’t be opened. Try another one.").assertExists()
        compose.onNodeWithText("Try again").assertDoesNotExist()
    }

    @Test
    fun `before the profile is read there is nothing to change`() {
        show(EditProfileUiState())

        compose.onNodeWithText("Add photo").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w360dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `English at 360dp and large text keeps the photo actions inside the screen, in whole words`() =
        assertFits(listOf("Change photo", "Remove photo"), "Only you see your profile photo.")

    @Test
    @Config(qualifiers = "hu-w360dp-h1400dp")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `Hungarian at 360dp and large text keeps the photo actions inside the screen, in whole words`() =
        assertFits(listOf("Fotó cseréje", "Fotó eltávolítása"), "A profilfotódat csak te látod.")

    private fun assertFits(buttons: List<String>, note: String) {
        show(loaded.copy(avatarPath = "p.jpg"), fontScale = 2f)

        val width = compose.onRoot().getBoundsInRoot().right
        (buttons + note).forEach {
            assertTrue("$it fits", compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot().right <= width)
        }
        // At 2x a Hungarian action may take two lines, but only ever breaks between its words.
        buttons.forEach {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(it, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
            val layout = results.single()
            (0 until layout.lineCount - 1).forEach { line -> assertEquals("$it breaks at a space", ' ', it[layout.getLineEnd(line) - 1]) }
        }
    }

    private fun jpeg(): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF5A4032.toInt()) }
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }
}
