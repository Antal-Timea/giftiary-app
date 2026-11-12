package app.giftiary.presentation.gifts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * The photos of a list, read only for the rows on screen: a row asks for its own photo while it is
 * composed ([photoOf]), so a history of hundreds of gifts reads none of their photos until rows show
 * them. A photo is read at most once at a time, and a row that leaves before its photo arrives
 * cancels the read. The [capacity] most recently shown photos stay in memory; an older one is read
 * again when its row comes back, from this device (a downloaded photo is kept there).
 *
 * One that cannot be read now is not remembered as missing: its row keeps the fallback, and the next
 * time the row is shown it is tried again.
 */
@Stable
class ShownPhotos(
    private val scope: CoroutineScope,
    private val load: suspend (path: String) -> ByteArray?,
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private val loaded = mutableStateMapOf<String, LoadedPhoto>()

    // Least recently shown first, so the oldest photo not on screen is dropped first.
    private val recent = LinkedHashSet<String>()
    private val rows = mutableMapOf<String, Int>()
    private val reads = mutableMapOf<String, Job>()

    /** The photo at [path] for a row being composed: null until it is read, or without a path. */
    @Composable
    fun photoOf(path: String?): LoadedPhoto? {
        if (path == null) return null
        DisposableEffect(this, path) {
            show(path)
            onDispose { hide(path) }
        }
        return loaded[path]
    }

    /** The photo at [path] if it is in memory, without asking for it. */
    operator fun get(path: String): LoadedPhoto? = loaded[path]

    internal fun show(path: String) {
        rows[path] = (rows[path] ?: 0) + 1
        if (path in loaded) {
            remember(path)
            return
        }
        if (reads[path]?.isActive == true) return
        reads[path] = scope.launch {
            val bytes = try {
                load(path)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (reads[path] == coroutineContext.job) reads.remove(path)
            if (bytes != null) {
                loaded[path] = LoadedPhoto(bytes)
                remember(path)
            }
        }
    }

    internal fun hide(path: String) {
        val left = (rows[path] ?: return) - 1
        if (left > 0) {
            rows[path] = left
            return
        }
        rows.remove(path)
        // Nobody waits for it any more: a download or read still running is not needed.
        reads.remove(path)?.cancel()
        trim()
    }

    private fun remember(path: String) {
        recent.remove(path)
        recent.add(path)
        trim()
    }

    private fun trim() {
        val iterator = recent.iterator()
        while (loaded.size > capacity && iterator.hasNext()) {
            val path = iterator.next()
            // A photo on screen is never dropped, even past the capacity.
            if (path in rows) continue
            iterator.remove()
            loaded.remove(path)
        }
    }

    companion object {
        /** About a screen and a half of rows; at most ~400 KB each, so a few MB of JPEG bytes. */
        const val DEFAULT_CAPACITY = 24

        /** Photos already in hand, for previews and screen tests: nothing else is ever read. */
        fun of(photos: Map<String, LoadedPhoto>): ShownPhotos =
            ShownPhotos(CoroutineScope(Dispatchers.Unconfined), { null }, capacity = Int.MAX_VALUE).apply {
                photos.forEach { (path, photo) -> loaded[path] = photo }
            }
    }
}
