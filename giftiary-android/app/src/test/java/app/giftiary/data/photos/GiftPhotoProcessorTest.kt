package app.giftiary.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File

/** Real decoding and encoding (Robolectric's native graphics): size, orientation, metadata. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GiftPhotoProcessorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val processor = GiftPhotoProcessor()

    @Test
    fun `a large photo is scaled to the longest side limit and stored as JPEG`() {
        val source = jpeg(width = 4000, height = 1000)

        val result = processor.process { source.inputStream() }

        val size = sizeOf(result)
        assertEquals(GiftPhotoProcessor.MAX_DIMENSION to 400, size)
        assertTrue("JPEG", result[0] == 0xFF.toByte() && result[1] == 0xD8.toByte())
        assertTrue(result.size <= GiftPhotoProcessor.TARGET_BYTES)
    }

    @Test
    fun `a small photo keeps its size`() {
        val result = processor.process { jpeg(width = 800, height = 600).inputStream() }

        assertEquals(800 to 600, sizeOf(result))
    }

    @Test
    fun `a photo taken sideways is stored upright, without its camera and location metadata`() {
        val source = jpeg(width = 1200, height = 800) {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MAKE, "Camera Maker")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "47/1,29/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        }

        val result = processor.process { source.inputStream() }

        assertEquals(800 to 1200, sizeOf(result))
        val exif = ExifInterface(ByteArrayInputStream(result))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        )
    }

    @Test(expected = GiftPhotoDecodeException::class)
    fun `something that is not an image is refused`() {
        processor.process { "not an image".byteInputStream() }
    }

    /** A JPEG of the given size, with [exif] written into it. */
    private fun jpeg(width: Int, height: Int, exif: ExifInterface.() -> Unit = {}): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200, 120, 90)) }
        val file = File(folder.root, "source-${System.nanoTime()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.path).apply(exif).saveAttributes()
        return file.readBytes()
    }

    private fun sizeOf(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }
}
