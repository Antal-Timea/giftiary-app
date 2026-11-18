package app.giftiary.presentation.giftideas

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.GiftIdeaStatus
import app.giftiary.domain.model.Money
import app.giftiary.domain.model.OccasionType
import app.giftiary.domain.repository.GiftIdeaEntry
import app.giftiary.domain.repository.OccasionDetails
import app.giftiary.presentation.gifts.ShownPhotos
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.presentation.wishlists.WISH_THUMBNAIL
import app.giftiary.testing.awaitPhoto
import app.giftiary.testing.ideaDetails
import app.giftiary.testing.ideaEntry
import app.giftiary.testing.occasion
import app.giftiary.testing.person
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/** An idea row with and without a photo: the thumbnail, its fallback, and the text beside it. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
// NATIVE: text widths and the photo's pixels are measured.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h1200dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftIdeaRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val anna = person("Anna")
    private val birthday = occasion(anna.id, OccasionDetails(OccasionType.BIRTHDAY, null, 12, 3, null, true))
    private val cover = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000001.jpg"
    private val other = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/00000000-0000-4000-8000-000000000002.jpg"

    private val book = entry("Book about Japanese gardens", GiftIdeaStatus.PLANNED, Money(12990, "HUF"), cover)
    private val pottery = entry("Pottery workshop", GiftIdeaStatus.IDEA, Money(25000, "HUF"), null)

    private val stored = mutableMapOf(cover to jpeg(0xFF8A5A2B.toInt()), other to jpeg(0xFF2B4F8A.toInt()))
    private val loads = mutableListOf<String>()
    private val photos = ShownPhotos(CoroutineScope(Dispatchers.Unconfined), { path -> loads += path; stored[path] })
    private val clicks = mutableListOf<String>()

    private var rows by mutableStateOf(listOf<GiftIdeaEntry>())
    private var dark by mutableStateOf(false)

    private fun show(vararg entries: GiftIdeaEntry) {
        rows = entries.toList()
        compose.setContent {
            GiftiaryTheme(darkTheme = dark) {
                Column {
                    rows.forEach { entry ->
                        GiftIdeaRow(
                            entry = entry,
                            onClick = { clicks += entry.idea.name },
                            showRecipient = false,
                            photo = photos.photoOf(entry.idea.photoPath),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a photo row leads with a wish-sized thumbnail, and a row without one stays text`() {
        show(book, pottery)
        compose.awaitPhoto(IDEA_PHOTO_TAG)

        assertEquals(1, compose.onAllNodesWithTag(IDEA_THUMBNAIL_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
        val thumbnail = compose.onNodeWithTag(IDEA_THUMBNAIL_TAG, useUnmergedTree = true).getBoundsInRoot()
        assertEquals("as large as a wish's", WISH_THUMBNAIL, IDEA_THUMBNAIL)
        assertEquals(IDEA_THUMBNAIL.value, (thumbnail.right - thumbnail.left).value, 0.5f)
        assertEquals(IDEA_THUMBNAIL.value, (thumbnail.bottom - thumbnail.top).value, 0.5f)
        assertTrue("the title beside it", left("Book about Japanese gardens") >= thumbnail.right)
        assertEquals("the text row starts where the thumbnail does", thumbnail.left.value, left("Pottery workshop").value, 0.5f)
        listOf("Planned", "Idea", "Birthday", "12,990", "25,000").forEach {
            val found = compose.onAllNodesWithText(it, substring = true, useUnmergedTree = true).fetchSemanticsNodes()
            assertTrue("$it shown", found.isNotEmpty())
        }
        assertEquals("only the row with a photo reads one", listOf(cover), loads)
    }

    @Test
    fun `the photo is cropped into the tile, and the whole row opens the idea`() {
        show(book, pottery)
        compose.awaitPhoto(IDEA_PHOTO_TAG)

        assertBrown(centre())
        compose.onNodeWithTag(IDEA_THUMBNAIL_TAG, useUnmergedTree = true).performClick()
        compose.onNodeWithText("Pottery workshop").performClick()
        assertEquals(listOf("Book about Japanese gardens", "Pottery workshop"), clicks)
    }

    @Test
    fun `while the photo is read or not available, the tile keeps its fallback and the text stays`() {
        stored.remove(cover)
        show(book)

        compose.onNodeWithTag(IDEA_THUMBNAIL_TAG, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(IDEA_PHOTO_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Book about Japanese gardens").assertExists()
        compose.onNodeWithText("Planned", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Birthday", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the row follows a replaced photo, and a removed one leaves a text row`() {
        show(book)
        compose.awaitPhoto(IDEA_PHOTO_TAG)
        assertBrown(centre())

        rows = listOf(book.copy(idea = book.idea.copy(photoPath = other)))
        compose.waitUntil(timeoutMillis = 5_000) { centre().blue > 0.4f }
        assertEquals("the replacement, not the old photo", 0x8A / 255f, centre().blue, 0.06f)

        rows = listOf(book.copy(idea = book.idea.copy(photoPath = null)))
        compose.onNodeWithTag(IDEA_THUMBNAIL_TAG, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Book about Japanese gardens").assertExists()
    }

    @Test
    @Config(qualifiers = "w320dp-h1200dp")
    fun `a long title wraps beside the thumbnail on a narrow screen, light and dark`() {
        val long = entry("A beautifully illustrated book about Japanese gardens, temples and their seasons", GiftIdeaStatus.IDEA, Money(12990, "HUF"), cover)
        show(long, pottery)
        listOf(false, true).forEach { isDark ->
            dark = isDark
            compose.awaitPhoto(IDEA_PHOTO_TAG)
            val width = compose.onRoot().getBoundsInRoot().right
            val title = compose.onNodeWithText(long.idea.name).getBoundsInRoot()
            assertTrue("dark=$isDark: fits, ${title.right} <= $width", title.right <= width)
            assertTrue("dark=$isDark: wraps onto more lines", title.bottom - title.top > 40.dp)
            val status = compose.onAllNodesWithText("Idea", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
            assertTrue("dark=$isDark: both rows keep their status", status.size == 2 && status.all { it.right <= width.value * density() })
        }
    }

    private fun entry(name: String, status: GiftIdeaStatus, price: Money, photoPath: String?) =
        ideaEntry(ideaDetails(anna, name = name, status = status, estimatedPrice = price, photoPath = photoPath), anna)
            .copy(occasion = birthday)

    private fun density() = compose.density.density

    private fun left(text: String) = compose.onNodeWithText(text, useUnmergedTree = true).getBoundsInRoot().left

    private fun centre() = compose.onNodeWithTag(IDEA_PHOTO_TAG, useUnmergedTree = true).captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }

    private fun assertBrown(colour: androidx.compose.ui.graphics.Color) {
        assertEquals(0x8A / 255f, colour.red, 0.06f)
        assertEquals(0x2B / 255f, colour.blue, 0.06f)
    }

    private fun jpeg(argb: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(argb) }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
