package app.giftiary.presentation.gifts

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** What a list reads, and keeps, for the rows it shows. */
class ShownPhotosTest {

    private val reads = mutableListOf<String>()
    private val cancelled = mutableListOf<String>()

    // A read waits here until the test lets it finish; without an entry it finishes at once.
    private val gates = mutableMapOf<String, CompletableDeferred<ByteArray?>>()
    private val stored = mutableMapOf<String, ByteArray>()

    private fun TestScope.photos(capacity: Int = ShownPhotos.DEFAULT_CAPACITY) =
        ShownPhotos(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), { path ->
            reads += path
            val gate = gates[path]
            if (gate == null) {
                stored[path]
            } else {
                try {
                    gate.await()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    cancelled += path
                    throw e
                }
            }
        }, capacity)

    @Test
    fun `nothing is read until a row shows it, and then only that row's photo`() = runTest {
        stored["a"] = byteArrayOf(1)
        stored["b"] = byteArrayOf(2)
        val photos = photos()
        assertEquals(emptyList<String>(), reads)

        photos.show("a")

        assertEquals(listOf("a"), reads)
        assertTrue(photos["a"]!!.bytes.contentEquals(byteArrayOf(1)))
        assertNull(photos["b"])
    }

    @Test
    fun `a photo being read is not read again for a second row or a recomposition`() = runTest {
        gates["a"] = CompletableDeferred()
        val photos = photos()

        photos.show("a")
        photos.show("a")
        gates.getValue("a").complete(byteArrayOf(1))
        photos.show("a")

        assertEquals(listOf("a"), reads)
        assertNotNull(photos["a"])
    }

    @Test
    fun `the same photo kept in memory is the same object, so the decoded image is not redone`() = runTest {
        stored["a"] = byteArrayOf(1)
        val photos = photos()
        photos.show("a")
        val first = photos["a"]
        photos.hide("a")

        photos.show("a")

        assertTrue(first === photos["a"])
        assertEquals(listOf("a"), reads)
    }

    @Test
    fun `a row that leaves before its photo arrives cancels the read, and nothing is kept`() = runTest {
        gates["a"] = CompletableDeferred()
        val photos = photos()

        photos.show("a")
        photos.hide("a")

        assertEquals(listOf("a"), cancelled)
        assertNull(photos["a"])

        // It comes back: read again, and this time it arrives.
        gates.remove("a")
        stored["a"] = byteArrayOf(3)
        photos.show("a")
        assertEquals(listOf("a", "a"), reads)
        assertTrue(photos["a"]!!.bytes.contentEquals(byteArrayOf(3)))
    }

    @Test
    fun `a read is kept while another row still shows the photo`() = runTest {
        gates["a"] = CompletableDeferred()
        val photos = photos()

        photos.show("a")
        photos.show("a")
        photos.hide("a")
        gates.getValue("a").complete(byteArrayOf(1))

        assertEquals(emptyList<String>(), cancelled)
        assertNotNull(photos["a"])
    }

    @Test
    fun `a photo that cannot be read now keeps the fallback and is tried again next time`() = runTest {
        val photos = ShownPhotos(CoroutineScope(UnconfinedTestDispatcher(testScheduler)), { path ->
            reads += path
            if (reads.size == 1) throw IOException("offline") else byteArrayOf(9)
        })

        photos.show("a")
        assertNull(photos["a"])
        photos.hide("a")
        photos.show("a")

        assertEquals(listOf("a", "a"), reads)
        assertNotNull(photos["a"])
    }

    @Test
    fun `a missing photo is not remembered as missing`() = runTest {
        val photos = photos()

        photos.show("gone")
        photos.hide("gone")
        photos.show("gone")

        assertEquals(listOf("gone", "gone"), reads)
        assertNull(photos["gone"])
    }

    @Test
    fun `only the most recently shown photos stay in memory, never one on screen`() = runTest {
        listOf("a", "b", "c", "d").forEach { stored[it] = byteArrayOf(1) }
        val photos = photos(capacity = 2)

        photos.show("a")
        photos.show("b")
        photos.show("c")
        // All three on screen: kept past the capacity.
        assertNotNull(photos["a"])
        assertNotNull(photos["b"])
        assertNotNull(photos["c"])

        photos.hide("a")
        assertNull("the oldest off screen goes", photos["a"])
        photos.hide("b")
        photos.hide("c")
        assertNotNull(photos["b"])
        assertNotNull(photos["c"])

        photos.show("b")
        photos.hide("b")
        photos.show("d")
        assertNull("c was shown longest ago", photos["c"])
        assertNotNull(photos["b"])
        assertNotNull(photos["d"])
    }

    @Test
    fun `photos in hand read nothing`() = runTest {
        val photo = LoadedPhoto(byteArrayOf(1))
        val photos = ShownPhotos.of(mapOf("a" to photo))

        photos.show("a")
        photos.show("b")

        assertTrue(photos["a"] === photo)
        assertNull(photos["b"])
    }
}
