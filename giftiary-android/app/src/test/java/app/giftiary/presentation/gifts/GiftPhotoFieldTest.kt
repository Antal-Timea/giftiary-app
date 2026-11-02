package app.giftiary.presentation.gifts

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.assertNear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * The editor's photo field with the system camera and picker replaced by a fake result registry,
 * and how a photo is sized. SDK 34: see GiftEditorLayoutTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftPhotoFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private val launched = mutableListOf<Any?>()
    private var cameraResult: Boolean? = true
    private var pickerResult: Uri? = Uri.parse("content://media/picker/1")
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launched += input
            when (contract) {
                is ActivityResultContracts.TakePicture -> {
                    val result = cameraResult ?: throw ActivityNotFoundException()
                    dispatchResult(requestCode, result)
                }
                is ActivityResultContracts.PickVisualMedia -> dispatchResult(requestCode, pickerResult)
            }
        }
    }

    private val events = mutableListOf<String>()
    private var targets = 0
    private val camera = GiftPhotoCamera(
        newTarget = { "content://app.giftiary.photos/camera/${++targets}.jpg" },
        onCaptured = { events += "captured $it" },
        onCancelled = { events += "cancelled $it" },
        onUnavailable = { events += "unavailable $it" },
    )

    // Robolectric's default screen is 320 dp wide.
    private fun showField(hasPhoto: Boolean = false, photo: LoadedPhoto? = null, width: Dp = 320.dp) {
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                GiftiaryTheme {
                    Box(Modifier.width(width)) {
                        GiftPhotoSources(onPick = { events += "picked $it" }, camera = camera) { choose ->
                            GiftPhotoTile(
                                hasPhoto = hasPhoto,
                                photo = photo,
                                busy = false,
                                enabled = true,
                                onChoose = choose,
                                onRemove = { events += "removed" },
                            )
                        }
                    }
                }
            }
        }
    }

    // ---- choosing a source ----

    @Test
    fun `adding a photo offers the camera and the gallery`() {
        showField()

        compose.onNodeWithText("Add photo").performClick()

        compose.onNodeWithText("Take photo").assertExists()
        compose.onNodeWithText("Choose from gallery").assertExists()
        assertTrue("nothing is launched before a choice", launched.isEmpty())
    }

    @Test
    fun `replacing a photo offers the same choice`() {
        showField(hasPhoto = true, photo = jpeg(400, 300))

        compose.onNodeWithText("Replace photo").performClick()

        compose.onNodeWithText("Take photo").assertExists()
        compose.onNodeWithText("Choose from gallery").assertExists()
    }

    @Test
    fun `the gallery is still the system photo picker`() {
        showField()

        compose.onNodeWithText("Add photo").performClick()
        compose.onNodeWithText("Choose from gallery").performClick()

        assertEquals(listOf("picked content://media/picker/1"), events)
        compose.onAllNodesWithText("Take photo").assertCountEquals(0)
    }

    // ---- camera ----

    @Test
    fun `a photo taken with the camera app is written to the target and handed over`() {
        showField()

        compose.onNodeWithText("Add photo").performClick()
        compose.onNodeWithText("Take photo").performClick()

        assertEquals("the camera app writes into the target", listOf(Uri.parse("content://app.giftiary.photos/camera/1.jpg")), launched)
        assertEquals(listOf("captured content://app.giftiary.photos/camera/1.jpg"), events)
    }

    @Test
    fun `a camera that returns without a photo cancels the capture`() {
        cameraResult = false
        showField()

        compose.onNodeWithText("Add photo").performClick()
        compose.onNodeWithText("Take photo").performClick()

        assertEquals(listOf("cancelled content://app.giftiary.photos/camera/1.jpg"), events)
    }

    @Test
    fun `without a camera app the capture is reported as unavailable`() {
        cameraResult = null
        showField()

        compose.onNodeWithText("Add photo").performClick()
        compose.onNodeWithText("Take photo").performClick()

        assertEquals(listOf("unavailable content://app.giftiary.photos/camera/1.jpg"), events)
    }

    // ---- sizing ----

    @Test
    fun `a portrait photo is shown whole at its own aspect ratio, no taller than the limit`() {
        showField(hasPhoto = true, photo = jpeg(width = 600, height = 1200))

        val bounds = compose.onNodeWithTag(PHOTO_TAG).getBoundsInRoot()
        assertNear(PHOTO_MAX_HEIGHT, bounds.bottom - bounds.top, "height")
        assertNear(PHOTO_MAX_HEIGHT / 2, bounds.right - bounds.left, "width")
    }

    @Test
    fun `a landscape photo takes the column's width at its own aspect ratio`() {
        showField(hasPhoto = true, photo = jpeg(width = 1600, height = 400), width = 300.dp)

        val bounds = compose.onNodeWithTag(PHOTO_TAG).getBoundsInRoot()
        assertNear(300.dp, bounds.right - bounds.left, "width")
        assertNear(75.dp, bounds.bottom - bounds.top, "height")
    }

    private fun jpeg(width: Int, height: Int): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2A5A52.toInt()) }
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }
}
