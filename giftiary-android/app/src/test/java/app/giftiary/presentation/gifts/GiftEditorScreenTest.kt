package app.giftiary.presentation.gifts

import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.core.app.ActivityOptionsCompat
import app.giftiary.domain.model.GiftDirection
import app.giftiary.domain.usecase.GiftInputError
import app.giftiary.presentation.components.WidthClass
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.awaitPhoto
import app.giftiary.testing.person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.time.LocalDate

/** The phone gift editor: quick capture in three steps, the optional details, and editing. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h3000dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftEditorScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val peter = person("Peter")
    private val mom = person("Mom")
    private val adding = GiftEditorUiState(
        isNew = true,
        status = GiftEditorStatus.EDITING,
        givenAt = LocalDate.of(2026, 10, 12),
        activePeople = listOf(peter, mom),
        peopleLoaded = true,
    )
    private val editing = adding.copy(
        isNew = false,
        personId = peter.id,
        name = "LEGO",
        savedName = "LEGO",
        price = "24990",
        note = "Wrapped in blue",
        productUrl = "https://example.org/lego",
    )

    private val events = mutableListOf<String>()
    private var state by mutableStateOf(adding)
    private var widthClass by mutableStateOf(WidthClass.COMPACT)
    private var shown = false

    // The camera app answers only when the test says so, as after a trip to another app.
    private var pendingCamera: Int? = null
    private val registry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            when (contract) {
                is ActivityResultContracts.TakePicture -> pendingCamera = requestCode
                is ActivityResultContracts.PickVisualMedia -> dispatchResult(requestCode, android.net.Uri.parse("content://media/picker/1"))
            }
        }
    }

    private fun show(state: GiftEditorUiState) {
        this.state = state
        if (shown) return
        shown = true
        compose.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides object : ActivityResultRegistryOwner {
                override val activityResultRegistry = registry
            }) {
                GiftiaryTheme {
                    GiftEditorScreen(state = this.state, actions = actions, widthClass = widthClass)
                }
            }
        }
    }

    private val actions = GiftEditorActions(
        onBack = { events += "back" },
        onOpenPeople = { events += "people" },
        onAddPerson = { events += "add person" },
        onDirectionChange = { events += "direction $it" },
        onPersonChange = { events += "person ${it.name}" },
        onOccasionChange = { events += "occasion" },
        onNameChange = { events += "name $it" },
        onDateChange = { events += "date" },
        onPriceChange = { events += "price $it" },
        onProductUrlChange = { events += "url $it" },
        onNoteChange = { events += "note $it" },
        onSave = { events += "save" },
        onWriteErrorShown = { events += "error shown" },
        onPhotoPicked = { events += "picked $it" },
        onNewCaptureTarget = { "content://app.giftiary.photos/camera/1.jpg" },
        onPhotoCaptured = { events += "captured $it" },
        onCaptureCancelled = { events += "cancelled $it" },
        onCameraUnavailable = { events += "unavailable" },
        onPhotoRemoved = { events += "removed" },
        onDeleteRequested = { events += "delete" },
        onDeleteDismissed = { events += "dismiss" },
        onDeleteConfirmed = { events += "confirm" },
    )

    private fun top(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().top

    private fun assertTopToBottom(texts: List<String>) {
        val tops = texts.map(::top)
        assertEquals("top to bottom: $texts", tops.sorted(), tops)
    }

    // ---- quick capture ----

    @Test
    fun `quick capture reads header, three steps, save, then more details`() {
        show(adding)

        assertTopToBottom(
            listOf(
                "Add a gift", "Capture the moment, keep the memory.", "Did you give or receive it?", "I gave",
                "Who was it for?", "Search people…", "Peter", "What was the gift?", "Gift name…",
                "e.g. LEGO, cookbook, concert tickets", "Save gift", "Add more details (optional)",
            ),
        )
        listOf("Occasion", "Date", "Price", "Photo", "Note", "Product link").forEach {
            compose.onNodeWithText(it, useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun `each step is a heading that says where it is`() {
        show(adding)

        listOf(1 to "Did you give or receive it?", 2 to "Who was it for?", 3 to "What was the gift?").forEach { (number, title) ->
            compose.onNode(hasText(title) and hasContentDescription("Step $number of 3"))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
    }

    @Test
    fun `the direction from Quick Add is already chosen, and the person step follows it`() {
        show(adding.copy(direction = GiftDirection.RECEIVED))

        compose.onNodeWithText("I received").assertIsSelected()
        compose.onNodeWithText("I gave").assertIsNotSelected()
        compose.onNodeWithText("Who was it from?").assertExists()
        compose.onNodeWithText("Who was it for?").assertDoesNotExist()

        compose.onNodeWithText("I gave").performClick()
        assertEquals(listOf("direction GIVEN"), events)
    }

    @Test
    fun `a person is chosen from the row, which the search narrows, and New adds one`() {
        show(adding)

        compose.onNodeWithText("Mom").performClick()
        compose.onNodeWithText("Search people…").performTextInput("pe")
        compose.onNodeWithContentDescription("Add a new person").performClick()
        assertEquals(listOf("person Mom", "add person"), events)

        show(adding.copy(personId = peter.id))
        compose.onNodeWithText("Peter").assertIsSelected()
        compose.onNodeWithText("Mom").assertDoesNotExist()
    }

    @Test
    fun `the search hides others but never the chosen person`() {
        show(adding.copy(personId = mom.id))

        compose.onNodeWithText("Search people…").performTextInput("pet")

        compose.onNodeWithText("Mom").assertIsSelected()
        compose.onNodeWithText("Peter").assertExists()

        show(adding.copy(personId = null))
        compose.onNodeWithText("Mom").assertDoesNotExist()
    }

    @Test
    fun `typing the name and saving reach the editor, errors stay with their step`() {
        show(adding.copy(errors = setOf(GiftInputError.RECIPIENT_REQUIRED, GiftInputError.NAME_REQUIRED)))

        assertTopToBottom(listOf("Who was it for?", "Choose who the gift was for", "What was the gift?", "Enter what the gift was"))
        compose.onNodeWithContentDescription("What was the gift?").performTextInput("Book")
        compose.onNodeWithText("Save gift").performClick()
        assertEquals(listOf("name Book", "save"), events)
    }

    // ---- details ----

    @Test
    fun `more details reveal the board's fields above save, and hiding them keeps every value`() {
        show(adding.copy(personId = peter.id, name = "LEGO", price = "24990", note = "Wrapped in blue"))

        compose.onNodeWithText("Add more details (optional)").performClick()
        assertTopToBottom(listOf("What was the gift?", "Fewer details", "Occasion", "Date", "Price", "Photo", "Note", "Product link", "Save gift"))
        compose.onNodeWithText("24990").assertExists()
        compose.onNodeWithText("HUF", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("No occasion", useUnmergedTree = true).assertExists()

        compose.onNodeWithText("Fewer details").performClick()
        compose.onNodeWithText("Price", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("LEGO").assertExists()
        compose.onNodeWithText("Add more details (optional)").performClick()
        compose.onNodeWithText("24990").assertExists()
        compose.onNodeWithText("Wrapped in blue").assertExists()
    }

    @Test
    fun `a price error opens the details so it is seen`() {
        show(adding.copy(price = "abc", priceError = PriceError.INVALID))

        compose.onNodeWithText("Enter a valid price", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Fewer details").assertExists()
    }

    @Test
    fun `typing in the details reaches the editor`() {
        show(editing.copy(price = "", note = "", productUrl = ""))

        compose.onNodeWithText("Price").performTextInput("100")
        compose.onNodeWithText("Note").performTextInput("Hi")
        compose.onNodeWithText("Product link").performScrollTo().performTextInput("x")
        // The fields are not fed back here, so each also reports being reset to its old value.
        assertEquals(listOf("price 100", "note Hi", "url x"), events.filterNot { it.endsWith(" ") })
    }

    // ---- photo ----

    @Test
    fun `the empty photo is an add tile offering the camera and the gallery, and the gallery is wired`() {
        show(adding.copy(personId = peter.id))
        compose.onNodeWithText("Add more details (optional)").performClick()

        compose.onNodeWithText("Add photo").performScrollTo().performClick()
        compose.onNodeWithText("Take photo").assertExists()
        compose.onNodeWithText("Choose from gallery").performClick()

        assertEquals(listOf("picked content://media/picker/1"), events)
    }

    @Test
    fun `a camera result after the layout changed still reaches the editor`() {
        show(adding.copy(personId = peter.id))
        compose.onNodeWithText("Add more details (optional)").performClick()
        compose.onNodeWithText("Add photo").performScrollTo().performClick()
        compose.onNodeWithText("Take photo").performClick()

        widthClass = WidthClass.EXPANDED
        compose.waitForIdle()
        registry.dispatchResult(pendingCamera!!, true)
        compose.waitForIdle()

        assertEquals(listOf("captured content://app.giftiary.photos/camera/1.jpg"), events)
    }

    @Test
    fun `a cancelled camera changes nothing but the capture`() {
        show(adding.copy(personId = peter.id, name = "LEGO"))
        compose.onNodeWithText("Add more details (optional)").performClick()
        compose.onNodeWithText("Add photo").performScrollTo().performClick()
        compose.onNodeWithText("Take photo").performClick()

        registry.dispatchResult(pendingCamera!!, false)
        compose.waitForIdle()

        assertEquals(listOf("cancelled content://app.giftiary.photos/camera/1.jpg"), events)
        compose.onNodeWithText("LEGO").assertExists()
        compose.onNodeWithText("Add photo").assertExists()
    }

    // ---- editing ----

    @Test
    fun `editing opens with the details and the gift's photo, which can be replaced or removed`() {
        show(editing.copy(photoPath = "photos/1.jpg", photo = jpeg(600, 900)))

        compose.onNodeWithText("Edit gift").assertExists()
        compose.onNodeWithText("Change what you remember about this gift.").assertExists()
        compose.onNodeWithText("Peter").assertIsSelected()
        listOf("LEGO", "24990", "Wrapped in blue", "https://example.org/lego").forEach { compose.onNodeWithText(it).assertExists() }
        compose.awaitPhoto(PHOTO_TAG)
        compose.onNodeWithTag(PHOTO_TAG).assertExists()

        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        compose.onNodeWithText("Replace photo").performClick()
        compose.onNodeWithText("Take photo").assertExists()
        assertEquals(listOf("removed"), events)
    }

    @Test
    fun `adding offers no delete`() {
        show(adding)

        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
    }

    @Test
    fun `editing keeps delete in the header's options, not beside Save`() {
        show(editing)

        compose.onNodeWithText("Delete", substring = true).assertDoesNotExist()
        val more = compose.onNodeWithContentDescription("More options").getBoundsInRoot()
        assertTrue("the options sit in the header, above the title", more.bottom <= top("Edit gift"))

        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete gift").performClick()
        assertEquals(listOf("delete"), events)
    }

    @Test
    fun `while an edit is saved, Save and Delete cannot be pressed again`() {
        show(editing.copy(isBusy = true))

        compose.onNode(hasText("Save gift") and hasClickAction()).assertIsNotEnabled().performClick()
        compose.onNodeWithContentDescription("More options").performClick()
        compose.onNodeWithText("Delete gift").assertIsNotEnabled().performClick()
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun `the confirmation names the gift, and cancel and delete reach the editor`() {
        show(editing.copy(name = "LEGO set", confirmingDelete = true))

        compose.onNodeWithText("Delete LEGO?").assertExists()
        compose.onNodeWithText("This gift will be removed from your history.").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("dismiss", "confirm"), events)
    }

    @Test
    fun `a failed delete is said and the form stays`() {
        show(editing.copy(writeError = GiftWriteError.DELETE_FAILED))

        compose.onNodeWithText("Could not delete. Please try again.").assertExists()
        compose.onNodeWithText("Save gift").assertExists()
        compose.mainClock.advanceTimeBy(10_000)
        assertTrue("the error is marked shown", "error shown" in events)
    }

    @Test
    fun `with nobody to choose, the editor sends the user to People`() {
        show(adding.copy(activePeople = emptyList()))

        compose.onNodeWithText("Go to People").performClick()
        compose.onNodeWithText("Save gift").assertDoesNotExist()
        assertEquals(listOf("people"), events)
    }

    @Test
    fun `back is the aqua circle`() {
        show(adding)

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }

    @Test
    @Config(qualifiers = "hu-w390dp-h3000dp")
    fun `Hungarian at large text stays inside the screen`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                GiftiaryTheme {
                    GiftEditorScreen(state = editing, actions = actions, widthClass = WidthClass.COMPACT)
                }
            }
        }

        val width = compose.onRoot().getBoundsInRoot().right
        listOf("Te adtad, vagy te kaptad?", "Én kaptam", "Kevesebb részlet", "Ajándék mentése").forEach {
            val bounds = compose.onNodeWithText(it, useUnmergedTree = true).getBoundsInRoot()
            assertTrue("$it fits: ${bounds.right} <= $width", bounds.right <= width)
        }
    }

    private fun jpeg(width: Int, height: Int): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2A5A52.toInt()) }
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }
}
