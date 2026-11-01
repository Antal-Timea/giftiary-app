package app.giftiary.data.photos

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.giftiary.data.local.GiftiaryDatabase
import app.giftiary.data.remote.RemoteAccounts
import app.giftiary.domain.repository.GiftPhotoUnreadableException
import app.giftiary.testing.FakeActiveOwner
import app.giftiary.testing.FakeGiftPhotoRemote
import app.giftiary.testing.MutableClock
import io.github.jan.supabase.annotations.SupabaseInternal
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Camera capture files: the URI the camera app gets, and the file's end after import or cancellation. */
@OptIn(SupabaseInternal::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CameraCaptureTest {

    private val alice = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val context = ApplicationProvider.getApplicationContext<Context>()
    // Capture files carry real modification times, so this clock starts at real time.
    private val clock = MutableClock(Instant.now())
    private val captures = CameraCaptures(context, clock)
    private val files = GiftPhotoFiles(context)
    private val database = Room.inMemoryDatabaseBuilder(context, GiftiaryDatabase::class.java).build()
    private val auth: Auth = createSupabaseClient("https://example.supabase.co", "publishable-key") {
        install(Auth) {
            sessionManager = MemorySessionManager()
            codeVerifierCache = MemoryCodeVerifierCache()
            autoLoadFromStorage = false
            autoSetupPlatform = false
            alwaysAutoRefresh = false
        }
    }.auth
    private val photos = GiftPhotoRepositoryImpl(
        context, files, GiftPhotoProcessor(), captures, database.photoTransferDao(), FakeGiftPhotoRemote(), RemoteAccounts(auth),
        FakeActiveOwner(alice),
    )

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the camera gets a content URI of this app's provider for a new file in the cache`() {
        val target = photos.newCaptureTarget()

        assertTrue(target, Regex("content://${context.packageName}\\.photos/camera/[0-9a-f-]{36}\\.jpg").matches(target))
        assertEquals(File(context.cacheDir, "camera/${target.substringAfterLast('/')}"), captures.fileFor(target))
        assertTrue("a new target each time", target != photos.newCaptureTarget())
    }

    @Test
    fun `the URI is served by a private FileProvider rooted at exactly the capture folder`() {
        val target = photos.newCaptureTarget().toUri()

        val provider = context.packageManager.resolveContentProvider(target.authority!!, PackageManager.GET_META_DATA)!!
        assertEquals("androidx.core.content.FileProvider", provider.name)
        assertFalse(provider.exported)
        assertTrue(provider.grantUriPermissions)
        // The URI's first segment names this root, which is how FileProvider finds the file.
        val paths = context.resources.getXml(provider.metaData.getInt("android.support.FILE_PROVIDER_PATHS"))
        val roots = buildList {
            while (paths.next() != XmlPullParser.END_DOCUMENT) {
                if (paths.eventType == XmlPullParser.START_TAG && paths.name != "paths") {
                    add("${paths.name} ${paths.getAttributeValue(null, "name")} ${paths.getAttributeValue(null, "path")}")
                }
            }
        }
        assertEquals(listOf("cache-path ${target.pathSegments[0]} camera/"), roots)
    }

    @Test
    fun `no other URI maps to a capture file`() {
        val target = photos.newCaptureTarget()
        val name = target.substringAfterLast('/')

        listOf(
            "content://other.app.photos/camera/$name",
            "content://${context.packageName}.photos/other/$name",
            "content://${context.packageName}.photos/camera/..%2F..%2Fdatabases%2Fgiftiary.db",
            "file://${context.cacheDir}/camera/$name",
        ).forEach { assertNull(it, captures.fileFor(it)) }
    }

    @Test
    fun `a captured photo goes through the same processing and its capture file is deleted`() = runTest {
        val target = photos.newCaptureTarget()
        val capture = captures.fileFor(target)!!
        writeJpeg(capture, width = 3000, height = 4000)

        val path = photos.importCapture(target)

        assertFalse("the capture file is gone", capture.exists())
        val stored = files.read(path)!!
        val size = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            .also { BitmapFactory.decodeByteArray(stored, 0, stored.size, it) }
        assertEquals(1200 to GiftPhotoProcessor.MAX_DIMENSION, size.outWidth to size.outHeight)
        assertEquals(alice, GiftPhotoFiles.ownerOf(path))
    }

    @Test
    fun `a capture that cannot be read is reported and its file is still deleted`() = runTest {
        val empty = photos.newCaptureTarget()
        // What a camera app that returned "OK" without writing leaves behind.
        captures.fileFor(empty)!!.apply { parentFile!!.mkdirs(); createNewFile() }
        val garbage = photos.newCaptureTarget()
        captures.fileFor(garbage)!!.writeText("not an image")

        listOf(empty, garbage).forEach { target ->
            val failure = runCatching { photos.importCapture(target) }.exceptionOrNull()
            assertTrue("$failure", failure is GiftPhotoUnreadableException)
            assertFalse(captures.fileFor(target)!!.exists())
        }
        assertTrue(files.list(alice).isEmpty())
    }

    @Test
    fun `a URI that is not a capture target is refused without reading it`() = runTest {
        val failure = runCatching { photos.importCapture("content://other.app/camera/x.jpg") }.exceptionOrNull()

        assertTrue("$failure", failure is GiftPhotoUnreadableException)
    }

    @Test
    fun `a cancelled capture's file is deleted`() = runTest {
        val target = photos.newCaptureTarget()
        val capture = captures.fileFor(target)!!.apply { parentFile!!.mkdirs(); writeText("partial") }

        photos.discardCapture(target)

        assertFalse(capture.exists())
    }

    @Test
    fun `captures whose result never came back are deleted after a day, recent ones are kept`() {
        val recent = captures.fileFor(photos.newCaptureTarget())!!.apply { parentFile!!.mkdirs(); writeText("open camera") }
        val abandoned = captures.fileFor(photos.newCaptureTarget())!!.apply { writeText("process died") }
        abandoned.setLastModified(clock.millis() - Duration.ofDays(2).toMillis())

        photos.newCaptureTarget()

        assertTrue(recent.exists())
        assertFalse(abandoned.exists())
    }

    private fun writeJpeg(file: File, width: Int, height: Int) {
        file.parentFile!!.mkdirs()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF2A5A52.toInt()) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }
}
