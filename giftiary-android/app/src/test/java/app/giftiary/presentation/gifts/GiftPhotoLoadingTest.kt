package app.giftiary.presentation.gifts

import android.graphics.Bitmap
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.giftiary.domain.model.GiftDirection
import app.giftiary.presentation.theme.GiftiaryTheme
import app.giftiary.testing.giftDetails
import app.giftiary.testing.giftEntry
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

/** How a list reads and decodes photos: only for its rows on screen, near the size they are drawn. */
// SDK 34: Espresso, which the Compose test rule drives input through, does not run on later SDKs here.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w390dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftPhotoLoadingTest {

    @get:Rule
    val compose = createComposeRule()

    private val martin = person("Martin")

    // A whole history, every gift with its own photo, one a day back from New Year.
    private val gifts = (0 until 200).map { day ->
        giftEntry(giftDetails(GiftDirection.GIVEN, martin, "Gift $day", LocalDate.of(2026, 12, 31).minusDays(day.toLong())), martin)
            .let { it.copy(gift = it.gift.copy(photoPath = "photo-$day.jpg")) }
    }
    private val reads = mutableListOf<String>()
    private val photo = jpeg(1600, 1200)

    @Test
    fun `a long history reads only the photos of the rows on screen, each once, and recomposing reads none`() {
        var state by mutableStateOf(GiftHistoryUiState(content = GiftHistoryContent.Loaded(groupByYearAndMonth(gifts))))
        compose.setContent {
            val scope = rememberCoroutineScope()
            val photos = remember { ShownPhotos(scope, { path -> reads += path; photo.bytes }) }
            GiftiaryTheme {
                GiftHistoryScreen(
                    state = state,
                    photos = photos,
                    onFilterChange = {},
                    onQueryChange = {},
                    onAddGift = {},
                    onOpenGift = {},
                    showAddButton = false,
                )
            }
        }
        compose.waitForIdle()

        val first = reads.toList()
        assertTrue("some rows are on screen: $first", first.isNotEmpty())
        assertTrue("only a screenful of 200 is read: ${first.size}", first.size < 20)
        assertEquals("each once", first.distinct(), first)
        assertEquals("the newest rows, from the top", gifts.take(first.size).map { it.gift.photoPath }, first)

        state = state.copy(query = "")
        state = state.copy(selectedGiftId = gifts.first().gift.id)
        compose.waitForIdle()

        assertEquals(first, reads)
    }

    @Test
    fun `scrolling reads the rows that come on screen, not those passed over`() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val shown = remember { ShownPhotos(scope, { path -> reads += path; photo.bytes }) }
            LazyColumn(Modifier.testTag("list")) {
                items(gifts) { entry ->
                    GiftMemoryCard(entry = entry, photo = shown.photoOf(entry.gift.photoPath), onClick = {}, modifier = Modifier.testTag(entry.gift.name))
                }
            }
        }
        compose.waitForIdle()
        val atTop = reads.size

        compose.onNodeWithTag("list").performScrollToIndex(199)
        compose.waitForIdle()

        assertTrue("the last rows are read", "photo-199.jpg" in reads)
        assertTrue("rows passed over are not: $reads", reads.size < atTop + 20)
        assertEquals("each once", reads.distinct(), reads)
    }

    @Test
    fun `a thumbnail is decoded near its size, the detail page decodes the photo whole`() {
        val thumbnail = decodePhoto(photo.bytes, targetPx = 156) as DecodedPhoto.Ready
        val whole = decodePhoto(photo.bytes, targetPx = null) as DecodedPhoto.Ready

        assertEquals(400, thumbnail.image.width)
        assertEquals(300, thumbnail.image.height)
        assertEquals(1600, whole.image.width)
        assertEquals(1200, whole.image.height)
    }

    @Test
    fun `bytes that are not an image are unreadable rather than a crash`() {
        assertEquals(DecodedPhoto.Unreadable, decodePhoto(byteArrayOf(1, 2, 3), targetPx = 100))
        assertEquals(DecodedPhoto.Unreadable, decodePhoto(ByteArray(0), targetPx = null))
    }

    @Test
    fun `the sample keeps the shorter side at least the target`() {
        assertEquals(1, sampleSizeFor(1600, 1200, null))
        assertEquals(1, sampleSizeFor(1600, 1200, 0))
        assertEquals(1, sampleSizeFor(1600, 1200, 1200))
        assertEquals(1, sampleSizeFor(1600, 1200, 700))
        assertEquals(2, sampleSizeFor(1600, 1200, 600))
        assertEquals(8, sampleSizeFor(1600, 1200, 150))
        assertEquals(4, sampleSizeFor(1200, 1600, 156))
        assertEquals(1, sampleSizeFor(100, 100, 640))
    }

    @Test
    fun `a photo is not decoded while composing, the first frame waits and then it shows`() {
        var firstFrame: DecodedPhoto? = null
        var latest: DecodedPhoto? = null
        compose.setContent {
            val decoded = rememberDecoded(photo, 52.dp)
            if (firstFrame == null) firstFrame = decoded
            latest = decoded
        }

        assertEquals(DecodedPhoto.Decoding, firstFrame)
        compose.waitUntil(5_000) { latest is DecodedPhoto.Ready }
        assertTrue((latest as DecodedPhoto.Ready).image.width < 1600)
    }

    private fun jpeg(width: Int, height: Int): LoadedPhoto {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return LoadedPhoto(ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray())
    }
}
