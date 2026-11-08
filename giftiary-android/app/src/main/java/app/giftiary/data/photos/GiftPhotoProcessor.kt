package app.giftiary.data.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * Turns a picked image into the photo Giftiary stores. A gift's photo ([process]) is the whole
 * picture, upright, at most [MAX_DIMENSION] pixels on its longer side, JPEG, normally well under
 * [TARGET_BYTES]. A person's ([processAvatar]) is an upright square cropped from its centre. Either
 * way re-encoding keeps no metadata (EXIF, location, camera), only the pixels.
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

        val upright = scaleAndOrient(decoded, orientationOf(open))

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

    /**
     * A person's photo: [AVATAR_SIZE] pixels square, cropped from the centre of the upright image,
     * JPEG at [AVATAR_QUALITY]. There is no cropping step for the user, so the centre is what a
     * portrait most likely shows; a smaller image is enlarged to the same size.
     *
     * @throws GiftPhotoDecodeException if it is not an image this device can decode.
     */
    fun processAvatar(open: () -> InputStream): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw GiftPhotoDecodeException()

        // Here the shorter side becomes the square, so it is the one that has to stay covered.
        var sample = 1
        while (min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= AVATAR_SIZE) sample *= 2
        val decoded = open().use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw GiftPhotoDecodeException()

        // Cropped and scaled before turning upright: a square turns into a square of the same size,
        // so the result is exactly AVATAR_SIZE whatever the orientation.
        val side = min(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, AVATAR_SIZE, AVATAR_SIZE, true)
        val orientation = orientationMatrix(orientationOf(open))
        val avatar = if (orientation.isIdentity) scaled else Bitmap.createBitmap(scaled, 0, 0, AVATAR_SIZE, AVATAR_SIZE, orientation, true)
        val bytes = avatar.encode(AVATAR_QUALITY)
        listOf(decoded, square, scaled, avatar).distinct().forEach(Bitmap::recycle)
        return bytes
    }

    private fun orientationOf(open: () -> InputStream): Int =
        runCatching { open().use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } }
            .getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun scaleAndOrient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        val longer = max(bitmap.width, bitmap.height)
        if (longer > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / longer
            matrix.postScale(scale, scale)
        }
        matrix.postConcat(orientationMatrix(orientation))
        if (matrix.isIdentity) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun orientationMatrix(orientation: Int): Matrix {
        val matrix = Matrix()
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
        return matrix
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

        /** A person's photo: sharp in the largest circle that shows it, on any screen. */
        const val AVATAR_SIZE = 640
        const val AVATAR_QUALITY = 85
    }
}

internal class GiftPhotoDecodeException : Exception("Not a readable image")
