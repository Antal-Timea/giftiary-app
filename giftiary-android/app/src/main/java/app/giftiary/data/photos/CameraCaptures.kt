package app.giftiary.data.photos

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Files the system camera app writes a new photo into, shared with it through the manifest's
 * androidx FileProvider (res/xml/camera_paths.xml).
 * Each lives only until the photo is imported or abandoned; nothing else reads them.
 */
@Singleton
class CameraCaptures @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {

    private val directory = File(context.cacheDir, DIRECTORY)
    private val authority = "${context.packageName}.photos"

    /** A content URI for a new, empty capture file. */
    fun newTarget(): String {
        // A capture whose result never came back, e.g. the process died while the camera was open.
        val cutoff = clock.millis() - ABANDONED_AFTER.toMillis()
        directory.listFiles()?.filter { it.lastModified() < cutoff }?.forEach(File::delete)
        directory.mkdirs()
        // FileProvider's own URI for this file: its path name, then the path below that root.
        return Uri.Builder().scheme("content").authority(authority).appendPath(DIRECTORY).appendPath("${UUID.randomUUID()}.jpg")
            .build().toString()
    }

    /** The file behind a URI from [newTarget]; null for any other URI. */
    fun fileFor(target: String): File? {
        val uri = target.toUri()
        if (uri.authority != authority) return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != DIRECTORY || !NAME.matches(segments[1])) return null
        return File(directory, segments[1])
    }

    fun delete(target: String) {
        fileFor(target)?.delete()
    }

    private companion object {
        // Also the path name in res/xml/camera_paths.xml, which is what the URI's first segment is.
        const val DIRECTORY = "camera"
        val NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg")
        val ABANDONED_AFTER: Duration = Duration.ofDays(1)
    }
}
