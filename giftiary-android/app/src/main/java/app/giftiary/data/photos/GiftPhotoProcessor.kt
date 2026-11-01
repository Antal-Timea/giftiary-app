package app.giftiary.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.max

/**
 * Turns a picked image into the photo Giftiary stores: upright, at most [MAX_DIMENSION] pixels on
 * its longer side, JPEG, normally well under [TARGET_BYTES]. Re-encoding keeps no metadata (EXIF,
 * location, camera), only the pixels.
 */
class GiftPhotoProcessor @Inject constructor() {

    /**
     * [open] is called more than once (size, orientation, pixels), each time for a fresh stream.
     *
     * @throws GiftPhotoDecodeException if it is not an image this device can decode.
     */
    fun process(open: () -> InputStream): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw GiftPhotoDecodeException()

        // Decode at the smallest power-of-two reduction that still covers MAX_DIMENSION: a large
        // camera photo never has to fit in memory at full size.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw GiftPhotoDecodeException()

        val orientation = runCatching { open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val upright = scaleAndOrient(decoded, orientation)

        var quality = INITIAL_QUALITY
        var bytes = upright.encode(quality)
        while (bytes.size > TARGET_BYTES && quality > MIN_QUALITY) {
            quality -= QUALITY_STEP
            bytes = upright.encode(quality)
        }
        if (upright !== decoded) decoded.recycle()
        upright.recycle()
        return bytes
    }

    private fun scaleAndOrient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        val longer = max(bitmap.width, bitmap.height)
        if (longer > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / longer
            matrix.postScale(scale, scale)
        }
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun Bitmap.encode(quality: Int): ByteArray =
        ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()

    companion object {
        /** Longer side in pixels: sharp on any phone or tablet screen. */
        const val MAX_DIMENSION = 1600

        /** Most photos land at 150–500 KB; the bucket accepts up to 2 MiB. */
        const val TARGET_BYTES = 1_000_000
        const val INITIAL_QUALITY = 85
        const val MIN_QUALITY = 60
        const val QUALITY_STEP = 10
    }
}

internal class GiftPhotoDecodeException : Exception("Not a readable image")
