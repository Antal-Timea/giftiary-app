package app.giftiary.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Gift photos. A photo is stored on this device first and attached to a gift by saving the gift
 * with its path ([GiftDetails.photoPath]); it then uploads in the background with the gift.
 */
interface GiftPhotoRepository {

    /**
     * Stores the image the user picked ([source]: a content URI) as a new photo of the signed-in
     * account, resized and without metadata, and returns its path. Nothing refers to it until a
     * gift is saved with it.
     *
     * @throws GiftPhotoUnreadableException if the image cannot be read.
     */
    suspend fun importPhoto(source: String): String

    /**
     * A content URI the system camera app can write a new photo into. Pass it to [importCapture]
     * once the camera returned a photo, or to [discardCapture] if it did not.
     */
    fun newCaptureTarget(): String

    /**
     * Like [importPhoto] for a photo the camera wrote into [target]; the capture file is deleted
     * whether or not it could be read.
     *
     * @throws GiftPhotoUnreadableException if the image cannot be read.
     */
    suspend fun importCapture(target: String): String

    /** Deletes the capture file of a camera that returned no photo. */
    suspend fun discardCapture(target: String)

    /**
     * The photo's JPEG bytes: from this device, or downloaded once and kept. Null if it is not
     * available now, e.g. offline on a device that has not seen it yet.
     */
    suspend fun loadPhoto(path: String): ByteArray?

    /** Whether the photo is still on this device only. */
    fun observeUploadPending(path: String): Flow<Boolean>
}

class GiftPhotoUnreadableException : Exception("The image could not be read")

/** The photo is not one this account imported on this device; it cannot be attached. */
class GiftPhotoUnavailableException : Exception("The photo is not available on this device")
