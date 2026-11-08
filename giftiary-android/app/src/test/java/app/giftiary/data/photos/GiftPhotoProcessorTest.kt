package app.giftiary.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    @Test
    fun `an avatar is a 640 pixel square JPEG cropped from the centre`() {
        // Left and right thirds red, the middle third blue: only the middle survives the crop.
        val source = jpeg(width = 3000, height = 1000, paint = { canvas -> stripes(canvas, vertical = true) })

        val result = processor.processAvatar { source.inputStream() }

        assertEquals(GiftPhotoProcessor.AVATAR_SIZE to GiftPhotoProcessor.AVATAR_SIZE, sizeOf(result))
        assertTrue("JPEG", result[0] == 0xFF.toByte() && result[1] == 0xD8.toByte())
        val avatar = BitmapFactory.decodeByteArray(result, 0, result.size)
        listOf(5, 320, 634).forEach { x -> assertTrue("blue at $x", isBlue(avatar.getPixel(x, 320))) }
    }

    @Test
    fun `a small or portrait image is still a 640 square`() {
        assertEquals(640 to 640, sizeOf(processor.processAvatar { jpeg(width = 300, height = 200).inputStream() }))
        assertEquals(640 to 640, sizeOf(processor.processAvatar { jpeg(width = 1000, height = 2500).inputStream() }))
    }

    @Test
    fun `an avatar taken sideways is upright, without metadata`() {
        // Stored landscape with blue on top; turned upright (90° clockwise) blue is on the right.
        val source = jpeg(width = 1000, height = 1000, paint = { canvas -> stripes(canvas, vertical = false, halves = true) }) {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MAKE, "Camera Maker")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE, "47/1,29/1,0/1")
            setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
        }

        val result = processor.processAvatar { source.inputStream() }

        val avatar = BitmapFactory.decodeByteArray(result, 0, result.size)
        assertTrue(isBlue(avatar.getPixel(600, 320)))
        assertTrue(!isBlue(avatar.getPixel(40, 320)))
        val exif = ExifInterface(ByteArrayInputStream(result))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))
        assertEquals(
            ExifInterface.ORIENTATION_UNDEFINED,
            exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED),
        )
    }

    @Test(expected = GiftPhotoDecodeException::class)
    fun `something that is not an image is refused as an avatar too`() {
        processor.processAvatar { "not an image".byteInputStream() }
    }

    @Test(expected = GiftPhotoDecodeException::class)
    fun `something that is not an image is refused`() {
        processor.process { "not an image".byteInputStream() }
    }

    /** A JPEG of the given size, painted by [paint], with [exif] written into it. */
    private fun jpeg(
        width: Int,
        height: Int,
        paint: (Canvas) -> Unit = { it.drawColor(Color.rgb(200, 120, 90)) },
        exif: ExifInterface.() -> Unit = {},
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { paint(Canvas(it)) }
        val file = File(folder.root, "source-${System.nanoTime()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file.path).apply(exif).saveAttributes()
        return file.readBytes()
    }

    /**
     * Red with blue across the middle third ([vertical] stripes) or, with [halves], blue over the
     * top half and red below.
     */
    private fun stripes(canvas: Canvas, vertical: Boolean, halves: Boolean = false) {
        canvas.drawColor(Color.RED)
        val blue = Paint().apply { color = Color.BLUE }
        val w = canvas.width.toFloat()
        val h = canvas.height.toFloat()
        when {
            halves -> canvas.drawRect(0f, 0f, w, h / 2, blue)
            vertical -> canvas.drawRect(w / 3, 0f, 2 * w / 3, h, blue)
            else -> canvas.drawRect(0f, h / 3, w, 2 * h / 3, blue)
        }
    }

    private fun isBlue(pixel: Int) = Color.blue(pixel) > 180 && Color.red(pixel) < 80

    private fun sizeOf(bytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth to options.outHeight
    }
}
